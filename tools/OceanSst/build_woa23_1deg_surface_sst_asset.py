#!/usr/bin/env python3
"""Build compact mean-annual SST asset from WOA23 1.0-degree annual NetCDF."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import h5py
import numpy as np
from scipy.ndimage import distance_transform_edt

DEFAULT_SOURCE_URL = (
    "https://www.ncei.noaa.gov/data/oceans/woa/WOA23/DATA/temperature/netcdf/"
    "decav91C0/1.00/woa23_decav91C0_t00_01.nc"
)
DEFAULT_NETCDF = Path("tools/OceanSst/woa23_decav91C0_t00_01.nc")
DEFAULT_OUT_BIN = Path(
    "common/src/main/resources/data/terrarium_expanded/ocean/woa23_sst_1deg_surface_annual_1991_2020.i16le"
)
DEFAULT_OUT_META = Path(
    "common/src/main/resources/data/terrarium_expanded/ocean/woa23_sst_1deg_surface_annual_1991_2020.json"
)

LAT_CELLS = 180
LON_CELLS = 360
SCALE = 0.01


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Extract WOA23 annual mean SST at 0 m depth, fill missing cells with nearest valid "
            "ocean cell (longitude wrap-aware), and save compact int16 asset."
        )
    )
    parser.add_argument(
        "--netcdf",
        type=Path,
        default=DEFAULT_NETCDF,
        help=f"Input NetCDF path (default: {DEFAULT_NETCDF})",
    )
    parser.add_argument(
        "--source-url",
        default=DEFAULT_SOURCE_URL,
        help="Source URL stored in output metadata.",
    )
    parser.add_argument(
        "--out-bin",
        type=Path,
        default=DEFAULT_OUT_BIN,
        help=f"Output int16 binary path (default: {DEFAULT_OUT_BIN})",
    )
    parser.add_argument(
        "--out-meta",
        type=Path,
        default=DEFAULT_OUT_META,
        help=f"Output metadata json path (default: {DEFAULT_OUT_META})",
    )
    return parser.parse_args()


def extract_surface_mean(path: Path) -> np.ndarray:
    with h5py.File(path, "r") as dataset:
        if "t_an" not in dataset:
            raise ValueError("Expected variable 't_an' in NetCDF")
        sst = np.array(dataset["t_an"][0, 0, :, :], dtype=np.float32)
        fill_value = float(dataset["t_an"].attrs["_FillValue"][0])
    sst[sst == fill_value] = np.nan
    if sst.shape != (LAT_CELLS, LON_CELLS):
        raise ValueError(f"Unexpected SST shape: {sst.shape}, expected {(LAT_CELLS, LON_CELLS)}")
    return sst


def fill_nan_nearest_wrap_lon(grid: np.ndarray) -> np.ndarray:
    valid = np.isfinite(grid)
    if valid.all():
        return grid
    if not valid.any():
        raise ValueError("SST grid has no valid values")

    # Tile longitudes 3x so nearest-neighbor fill can cross the dateline.
    tiled = np.concatenate([grid, grid, grid], axis=1)
    tiled_valid = np.isfinite(tiled)
    tiled_invalid = ~tiled_valid
    nearest = distance_transform_edt(tiled_invalid, return_distances=False, return_indices=True)
    filled = tiled.copy()
    filled[tiled_invalid] = tiled[tuple(nearest[:, tiled_invalid])]
    center = filled[:, LON_CELLS : 2 * LON_CELLS]
    return center.astype(np.float32, copy=False)


def quantize_int16(grid: np.ndarray) -> np.ndarray:
    quantized = np.rint(grid / SCALE).astype(np.int32)
    if quantized.min() < -32767 or quantized.max() > 32767:
        raise ValueError("Quantized SST values exceeded int16 bounds")
    return quantized.astype("<i2")


def main() -> None:
    args = parse_args()
    if not args.netcdf.exists():
        raise FileNotFoundError(
            f"Missing NetCDF file: {args.netcdf}\n"
            f"Download it from: {args.source_url}"
        )

    raw = extract_surface_mean(args.netcdf)
    filled = fill_nan_nearest_wrap_lon(raw)
    packed = quantize_int16(filled)

    args.out_bin.parent.mkdir(parents=True, exist_ok=True)
    args.out_bin.write_bytes(packed.tobytes(order="C"))

    metadata = {
        "dataset": "NOAA WOA23",
        "product": "temperature",
        "period": "1991-2020 climate normal (decav91C0)",
        "field": "t_an",
        "depth_m": 0.0,
        "units": "degrees_celsius",
        "grid": {
            "rows_lat": LAT_CELLS,
            "cols_lon": LON_CELLS,
            "lat_start_center": -89.5,
            "lat_step": 1.0,
            "lon_start_center": -179.5,
            "lon_step": 1.0,
        },
        "encoding": {
            "type": "int16_le",
            "scale_celsius_per_unit": SCALE,
            "layout": "row_major_lat_then_lon",
        },
        "source_url": args.source_url,
    }
    args.out_meta.parent.mkdir(parents=True, exist_ok=True)
    args.out_meta.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")

    print(f"Wrote SST asset: {args.out_bin} ({args.out_bin.stat().st_size} bytes)")
    print(f"Wrote metadata:  {args.out_meta}")


if __name__ == "__main__":
    main()
