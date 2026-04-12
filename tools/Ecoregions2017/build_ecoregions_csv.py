#!/usr/bin/env python3
"""Build ecoregions-unique-colors.csv directly from Ecoregions2017 shapefile."""

from __future__ import annotations

import argparse
import colorsys
import csv
from dataclasses import dataclass
from pathlib import Path

import geopandas as gpd

CSV_FIELDS = [
    "ECO_ID",
    "ECO_NAME",
    "BIOME_NUM",
    "BIOME_NAME",
    "REALM",
    "COLOR",
    "COLOR_BIO",
    "UNIQUE_ECOREGION_COLOR",
]


@dataclass(frozen=True)
class MetadataRow:
    eco_id: int
    eco_name: str
    biome_num: int
    biome_name: str
    realm: str
    color: str
    color_bio: str


def normalize_eco_id(value: object) -> int:
    text = str(value).strip()
    if not text:
        raise ValueError("Empty ECO_ID value")
    number = float(text)
    if not number.is_integer():
        raise ValueError(f"ECO_ID must be integral, got {value!r}")
    return int(number)


def normalize_biome_num(value: object) -> int:
    text = str(value).strip()
    if not text:
        raise ValueError("Empty BIOME_NUM value")
    number = float(text)
    if not number.is_integer():
        raise ValueError(f"BIOME_NUM must be integral, got {value!r}")
    return int(number)


def parse_hex_rgb(hex_value: str) -> tuple[int, int, int]:
    value = hex_value.strip()
    if len(value) != 7 or not value.startswith("#"):
        raise ValueError(f"Invalid hex color {hex_value!r}; expected #RRGGBB")
    return (int(value[1:3], 16), int(value[3:5], 16), int(value[5:7], 16))


def normalize_hex_color(hex_value: str) -> str:
    r, g, b = parse_hex_rgb(hex_value)
    return f"#{r:02X}{g:02X}{b:02X}"


def rgb_to_int(rgb: tuple[int, int, int]) -> int:
    return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]


def color_int_to_hex(value: int) -> str:
    return f"#{value:06X}"


def color_distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    dr = a[0] - b[0]
    dg = a[1] - b[1]
    db = a[2] - b[2]
    return float((dr * dr + dg * dg + db * db) ** 0.5)


def make_candidate_rgb(
    base_rgb: tuple[int, int, int], eco_id: int, variant_index: int
) -> tuple[int, int, int]:
    base_r, base_g, base_b = base_rgb
    h, s, v = colorsys.rgb_to_hsv(base_r / 255.0, base_g / 255.0, base_b / 255.0)

    seed = (eco_id * 2654435761 + variant_index * 1013904223) & 0xFFFFFFFF
    f1 = ((seed & 0x3FF) / 1023.0) - 0.5
    f2 = (((seed >> 10) & 0x3FF) / 1023.0) - 0.5
    f3 = (((seed >> 20) & 0x3FF) / 1023.0) - 0.5

    hue = (h + f1 * 0.12) % 1.0
    sat = min(1.0, max(0.20, s + f2 * 0.35))
    val = min(1.0, max(0.20, v + f3 * 0.35))
    out_r, out_g, out_b = colorsys.hsv_to_rgb(hue, sat, val)
    return (
        int(round(out_r * 255.0)),
        int(round(out_g * 255.0)),
        int(round(out_b * 255.0)),
    )


def extract_rows_from_shapefile(shp_path: Path) -> list[MetadataRow]:
    gdf = gpd.read_file(shp_path)
    required = ["ECO_ID", "ECO_NAME", "BIOME_NUM", "BIOME_NAME", "REALM", "COLOR", "COLOR_BIO"]
    missing = [name for name in required if name not in gdf.columns]
    if missing:
        raise ValueError(f"Shapefile missing required columns: {missing}")

    by_eco_id: dict[int, MetadataRow] = {}
    for raw in gdf.itertuples(index=False):
        eco_id = normalize_eco_id(getattr(raw, "ECO_ID"))
        row = MetadataRow(
            eco_id=eco_id,
            eco_name=str(getattr(raw, "ECO_NAME")).strip(),
            biome_num=normalize_biome_num(getattr(raw, "BIOME_NUM")),
            biome_name=str(getattr(raw, "BIOME_NAME")).strip(),
            realm=str(getattr(raw, "REALM")).strip(),
            color=normalize_hex_color(str(getattr(raw, "COLOR")).strip()),
            color_bio=normalize_hex_color(str(getattr(raw, "COLOR_BIO")).strip()),
        )
        existing = by_eco_id.get(eco_id)
        if existing is None:
            by_eco_id[eco_id] = row
            continue
        if existing != row:
            raise ValueError(
                f"Conflicting metadata for ECO_ID={eco_id}: {existing!r} != {row!r}"
            )
    return [by_eco_id[eco_id] for eco_id in sorted(by_eco_id)]


def assign_unique_colors(rows: list[MetadataRow]) -> dict[int, int]:
    used_color_ints: set[int] = {0}
    mapping: dict[int, int] = {}

    # Order by ECO_ID for stable, byte-identical output between runs.
    for row in sorted(rows, key=lambda value: value.eco_id):
        base_rgb = parse_hex_rgb(row.color_bio)
        chosen: tuple[int, int, int] | None = None
        for variant_index in range(1, 1_000_001):
            candidate = make_candidate_rgb(base_rgb, row.eco_id, variant_index)
            candidate_int = rgb_to_int(candidate)
            if candidate_int in used_color_ints:
                continue
            distance = color_distance(candidate, base_rgb)
            if distance < 16.0:
                continue
            if distance > 130.0:
                continue
            chosen = candidate
            break
        if chosen is None:
            raise ValueError(
                f"Failed to assign unique color for ECO_ID={row.eco_id} near COLOR_BIO={row.color_bio}"
            )
        chosen_int = rgb_to_int(chosen)
        mapping[row.eco_id] = chosen_int
        used_color_ints.add(chosen_int)
    return mapping


def write_csv(rows: list[MetadataRow], color_by_eco_id: dict[int, int], out_csv: Path) -> None:
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in sorted(rows, key=lambda value: value.eco_id):
            unique_color = color_by_eco_id.get(row.eco_id)
            if unique_color is None:
                raise ValueError(f"Missing unique color mapping for ECO_ID={row.eco_id}")
            writer.writerow(
                {
                    "ECO_ID": row.eco_id,
                    "ECO_NAME": row.eco_name,
                    "BIOME_NUM": row.biome_num,
                    "BIOME_NAME": row.biome_name,
                    "REALM": row.realm,
                    "COLOR": row.color,
                    "COLOR_BIO": row.color_bio,
                    "UNIQUE_ECOREGION_COLOR": color_int_to_hex(unique_color),
                }
            )


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Generate ecoregions-unique-colors.csv from Ecoregions2017 shapefile."
    )
    parser.add_argument(
        "--shp",
        type=Path,
        default=script_dir / "shapefiles" / "Ecoregions2017.shp",
        help="Input shapefile path.",
    )
    parser.add_argument(
        "--out-csv",
        type=Path,
        default=script_dir / "ecoregions-unique-colors.csv",
        help="Output CSV path.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    rows = extract_rows_from_shapefile(args.shp)
    color_by_eco_id = assign_unique_colors(rows)
    write_csv(rows, color_by_eco_id, args.out_csv)
    print(f"Wrote {len(rows)} rows to {args.out_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
