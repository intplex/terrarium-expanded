#!/usr/bin/env python3
"""Rasterize Ecoregions2017 polygons into z/x/y PNG tiles using UNIQUE_ECOREGION_COLOR."""

from __future__ import annotations

import argparse
import csv
import time
from pathlib import Path

import geopandas as gpd
import numpy as np
from PIL import Image
from rasterio import features
from rasterio.transform import from_bounds
from shapely.geometry import box
from shapely.geometry.base import BaseGeometry

WEB_MERCATOR_HALF_WORLD = 20037508.342789244
TILE_SIZE = 256
REQUIRED_COLUMNS = [
    "ECO_ID",
    "ECO_NAME",
    "BIOME_NUM",
    "BIOME_NAME",
    "REALM",
    "COLOR",
    "COLOR_BIO",
    "UNIQUE_ECOREGION_COLOR",
]


def normalize_eco_id(value: object) -> int:
    text = str(value).strip()
    if not text:
        raise ValueError("Empty ECO_ID value")
    number = float(text)
    if not number.is_integer():
        raise ValueError(f"ECO_ID must be integral, got {value!r}")
    return int(number)


def parse_hex_rgb(hex_value: str) -> tuple[int, int, int]:
    value = hex_value.strip()
    if len(value) != 7 or not value.startswith("#"):
        raise ValueError(f"Invalid hex color {hex_value!r}; expected #RRGGBB")
    return (int(value[1:3], 16), int(value[3:5], 16), int(value[5:7], 16))


def rgb_to_int(rgb: tuple[int, int, int]) -> int:
    return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]


def parse_unique_color_csv(csv_path: Path) -> dict[int, int]:
    color_by_eco_id: dict[int, int] = {}
    with csv_path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        missing = [name for name in REQUIRED_COLUMNS if name not in (reader.fieldnames or [])]
        if missing:
            raise ValueError(f"CSV missing required columns: {missing}")

        for raw in reader:
            eco_id = normalize_eco_id(raw["ECO_ID"])
            if eco_id in color_by_eco_id:
                raise ValueError(f"Duplicate ECO_ID in CSV: {eco_id}")
            color_by_eco_id[eco_id] = rgb_to_int(parse_hex_rgb(raw["UNIQUE_ECOREGION_COLOR"]))
    return color_by_eco_id


def column_bounds_3857(zoom: int, x: int) -> tuple[float, float, float, float]:
    tiles_per_axis = 1 << zoom
    tile_span = (2.0 * WEB_MERCATOR_HALF_WORLD) / tiles_per_axis
    min_x = -WEB_MERCATOR_HALF_WORLD + x * tile_span
    max_x = min_x + tile_span
    return min_x, -WEB_MERCATOR_HALF_WORLD, max_x, WEB_MERCATOR_HALF_WORLD


def rasterize_column(
    gdf_3857: gpd.GeoDataFrame,
    column_polygon: BaseGeometry,
    bounds: tuple[float, float, float, float],
    color_by_eco_id: dict[int, int],
    total_height_px: int,
) -> np.ndarray:
    candidate_indices = [int(idx) for idx in gdf_3857.sindex.intersection(column_polygon.bounds)]
    if not candidate_indices:
        return np.zeros((total_height_px, TILE_SIZE), dtype=np.uint32)

    subset = gdf_3857.iloc[candidate_indices]
    subset = subset[subset.geometry.intersects(column_polygon)]
    if subset.empty:
        return np.zeros((total_height_px, TILE_SIZE), dtype=np.uint32)

    subset = subset.sort_values(by=["_eco_id", "_feature_order"], kind="stable")
    shapes = (
        (geom, int(color_by_eco_id[int(eco_id)]))
        for geom, eco_id in zip(subset.geometry, subset["_eco_id"])
        if geom is not None and not geom.is_empty
    )
    transform = from_bounds(*bounds, width=TILE_SIZE, height=total_height_px)
    return features.rasterize(
        shapes=shapes,
        out_shape=(total_height_px, TILE_SIZE),
        transform=transform,
        fill=0,
        all_touched=True,
        dtype=np.uint32,
    )


def write_png_from_color_ids(color_ids: np.ndarray, out_path: Path) -> None:
    red = ((color_ids >> 16) & 0xFF).astype(np.uint8)
    green = ((color_ids >> 8) & 0xFF).astype(np.uint8)
    blue = (color_ids & 0xFF).astype(np.uint8)
    rgb = np.stack([red, green, blue], axis=-1)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgb, mode="RGB").save(out_path, format="PNG")


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=(
            "Rasterize Ecoregions shapefile to z/x/y PNG tiles using UNIQUE_ECOREGION_COLOR from CSV."
        )
    )
    parser.add_argument(
        "--shp",
        type=Path,
        default=script_dir / "shapefiles" / "Ecoregions2017.shp",
        help="Input shapefile path.",
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=script_dir / "ecoregions-unique-colors.csv",
        help="Input CSV containing UNIQUE_ECOREGION_COLOR.",
    )
    parser.add_argument(
        "--out-root",
        type=Path,
        default=script_dir / "tiles",
        help="Output tile root; tiles are written as {out-root}/{z}/{x}/{y}.png",
    )
    parser.add_argument(
        "--zoom",
        type=int,
        default=8,
        help="Slippy-map zoom level to rasterize.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.zoom < 0:
        raise ValueError("--zoom must be >= 0")

    color_by_eco_id = parse_unique_color_csv(args.csv)

    gdf = gpd.read_file(args.shp)
    if "ECO_ID" not in gdf.columns:
        raise ValueError("Input shapefile does not have ECO_ID column")
    if gdf.crs is None:
        raise ValueError("Input shapefile has no CRS; expected EPSG:4326 or equivalent")

    gdf = gdf.reset_index(drop=True)
    gdf["_feature_order"] = np.arange(len(gdf), dtype=np.int64)
    gdf["_eco_id"] = gdf["ECO_ID"].map(normalize_eco_id)

    metadata_eco_ids = set(color_by_eco_id)
    shape_eco_ids = set(gdf["_eco_id"])
    missing_in_csv = sorted(shape_eco_ids - metadata_eco_ids)
    if missing_in_csv:
        raise ValueError(
            f"Shapefile contains ECO_ID values missing from CSV (first 10): {missing_in_csv[:10]}"
        )
    missing_in_shapes = sorted(metadata_eco_ids - shape_eco_ids)
    if missing_in_shapes:
        raise ValueError(
            f"CSV contains ECO_ID values missing from shapefile (first 10): {missing_in_shapes[:10]}"
        )

    gdf_3857 = gdf.to_crs(epsg=3857)
    tiles_per_axis = 1 << args.zoom
    total_height_px = tiles_per_axis * TILE_SIZE
    total_tiles = tiles_per_axis * tiles_per_axis

    run_start = time.perf_counter()
    written = 0
    for x in range(tiles_per_axis):
        column_bounds = column_bounds_3857(args.zoom, x)
        column_polygon = box(*column_bounds)
        column_ids = rasterize_column(
            gdf_3857=gdf_3857,
            column_polygon=column_polygon,
            bounds=column_bounds,
            color_by_eco_id=color_by_eco_id,
            total_height_px=total_height_px,
        )
        for y in range(tiles_per_axis):
            start_row = y * TILE_SIZE
            end_row = start_row + TILE_SIZE
            out_path = args.out_root / str(args.zoom) / str(x) / f"{y}.png"
            write_png_from_color_ids(column_ids[start_row:end_row, :], out_path)
            written += 1

        if (x + 1) % 16 == 0 or (x + 1) == tiles_per_axis:
            elapsed = max(1e-9, time.perf_counter() - run_start)
            rate = written / elapsed
            print(
                f"[progress] columns={x + 1}/{tiles_per_axis} tiles={written}/{total_tiles} "
                f"rate={rate:.1f} tiles/s"
            )

    print(f"Wrote {written} tile PNGs to {args.out_root / str(args.zoom)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
