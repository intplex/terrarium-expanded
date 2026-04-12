#!/usr/bin/env python3
"""Build runtime color->biome mapping from ecoregion batch CSV files."""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class MappingRow:
    color_hex: str
    eco_name: str
    biome_name: str
    realm: str
    bop_biome_id: str
    minecraft_biome_id: str


def normalize_color_hex(raw: str) -> str:
    value = raw.strip().upper()
    if len(value) != 7 or not value.startswith("#"):
        raise ValueError(f"Invalid UNIQUE_ECOREGION_COLOR {raw!r}; expected #RRGGBB")
    int(value[1:], 16)
    return value


def normalize_biome_id(raw: str, label: str) -> str:
    value = raw.strip()
    if not value:
        raise ValueError(f"{label} cannot be blank")
    if ":" not in value:
        raise ValueError(f"Invalid {label} {raw!r}; expected namespace:path")
    return value


def normalize_optional_biome_id(raw: str, label: str) -> str:
    value = raw.strip()
    if not value:
        return ""
    if ":" not in value:
        raise ValueError(f"Invalid {label} {raw!r}; expected namespace:path")
    return value


def load_valid_bop_biome_ids(path: Path) -> set[str]:
    if not path.exists():
        raise ValueError(f"Missing Biomes O' Plenty biome list CSV: {path}")
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if "biome_id" not in set(reader.fieldnames or []):
            raise ValueError(f"{path} missing required column 'biome_id'")
        valid_ids = {
            normalize_biome_id(row["biome_id"], "biome_id")
            for row in reader
            if row.get("biome_id") and row["biome_id"].strip()
        }
    if not valid_ids:
        raise ValueError(f"No biome_id values found in {path}")
    return valid_ids


def load_rows(batch_dir: Path, valid_bop_biome_ids: set[str]) -> list[MappingRow]:
    batch_paths = sorted(batch_dir.glob("unique_ecoregions_batch_*.csv"))
    if not batch_paths:
        raise ValueError(f"No batch files found in {batch_dir}")

    by_color: dict[str, MappingRow] = {}
    for path in batch_paths:
        with path.open("r", encoding="utf-8", newline="") as handle:
            reader = csv.DictReader(handle)
            required = {
                "UNIQUE_ECOREGION_COLOR",
                "ECO_NAME",
                "BIOME_NAME",
                "REALM",
                "BIOMES_O_PLENTY_BIOME",
                "MINECRAFT_BIOME",
            }
            if not required.issubset(set(reader.fieldnames or [])):
                raise ValueError(f"{path} missing columns {sorted(required)}")

            for line_number, row in enumerate(reader, start=2):
                color_hex = normalize_color_hex(row["UNIQUE_ECOREGION_COLOR"])
                eco_name = row["ECO_NAME"].strip()
                biome_name = row["BIOME_NAME"].strip()
                realm = row["REALM"].strip()
                bop_biome_id = normalize_optional_biome_id(
                    row["BIOMES_O_PLENTY_BIOME"],
                    "BIOMES_O_PLENTY_BIOME",
                )
                minecraft_biome_id = normalize_biome_id(row["MINECRAFT_BIOME"], "MINECRAFT_BIOME")
                mapping = MappingRow(
                    color_hex=color_hex,
                    eco_name=eco_name,
                    biome_name=biome_name,
                    realm=realm,
                    bop_biome_id=bop_biome_id,
                    minecraft_biome_id=minecraft_biome_id,
                )
                if bop_biome_id:
                    if not bop_biome_id.startswith("biomesoplenty:"):
                        raise ValueError(
                            "BIOMES_O_PLENTY_BIOME must use 'biomesoplenty:' namespace "
                            f"({path}:{line_number}, value={bop_biome_id!r})"
                        )
                    if bop_biome_id not in valid_bop_biome_ids:
                        raise ValueError(
                            "Unknown BIOMES_O_PLENTY_BIOME in batch CSV "
                            f"({path}:{line_number}, value={bop_biome_id!r})"
                        )
                existing = by_color.get(color_hex)
                if existing is not None and existing != mapping:
                    raise ValueError(
                        f"Conflicting biome mapping for color {color_hex}: "
                        f"{existing!r} vs {mapping!r} ({path}:{line_number})"
                    )
                by_color[color_hex] = mapping

    return [by_color[color_hex] for color_hex in sorted(by_color)]


def write_rows(rows: list[MappingRow], out_csv: Path) -> None:
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=[
                "UNIQUE_ECOREGION_COLOR",
                "ECO_NAME",
                "BIOME_NAME",
                "REALM",
                "BIOMES_O_PLENTY_BIOME",
                "MINECRAFT_BIOME",
            ],
        )
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    "UNIQUE_ECOREGION_COLOR": row.color_hex,
                    "ECO_NAME": row.eco_name,
                    "BIOME_NAME": row.biome_name,
                    "REALM": row.realm,
                    "BIOMES_O_PLENTY_BIOME": row.bop_biome_id,
                    "MINECRAFT_BIOME": row.minecraft_biome_id,
                }
            )


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=(
            "Merge Ecoregions batch CSV files into one runtime color->biome CSV "
            "for mod resources."
        )
    )
    parser.add_argument(
        "--batch-dir",
        type=Path,
        default=script_dir / "batches",
        help="Directory containing unique_ecoregions_batch_*.csv files.",
    )
    parser.add_argument(
        "--out-csv",
        type=Path,
        default=script_dir.parent.parent
        / "common"
        / "src"
        / "main"
        / "resources"
        / "data"
        / "terrarium_expanded"
        / "ecoregions"
        / "color_biome_map.csv",
        help="Output runtime CSV path.",
    )
    parser.add_argument(
        "--bop-biomes-csv",
        type=Path,
        default=script_dir / "biomes_o_plenty_biomes.csv",
        help="CSV containing valid Biomes O' Plenty biome IDs (column: biome_id).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    valid_bop_biome_ids = load_valid_bop_biome_ids(args.bop_biomes_csv)
    rows = load_rows(args.batch_dir, valid_bop_biome_ids)
    write_rows(rows, args.out_csv)
    print(f"Wrote {len(rows)} rows to {args.out_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
