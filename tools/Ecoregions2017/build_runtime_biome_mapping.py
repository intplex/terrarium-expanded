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
    bop_priority: str
    regions_unexplored_biome_id: str
    regions_unexplored_priority: str
    natures_spirit_biome_id: str
    natures_spirit_priority: str
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


def normalize_optional_priority(raw: str, biome_id: str, biome_label: str, priority_label: str) -> str:
    value = raw.strip()
    if not biome_id:
        if value:
            raise ValueError(f"{priority_label} must be blank when {biome_label} is blank")
        return ""
    if not value:
        raise ValueError(f"{priority_label} cannot be blank when {biome_label} is set")
    try:
        parsed = int(value)
    except ValueError as exc:
        raise ValueError(f"Invalid {priority_label} {raw!r}; expected non-negative integer") from exc
    if parsed < 0:
        raise ValueError(f"Invalid {priority_label} {raw!r}; expected non-negative integer")
    return str(parsed)


def load_valid_biome_ids(path: Path, label: str) -> set[str]:
    if not path.exists():
        raise ValueError(f"Missing {label} biome list CSV: {path}")
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


def validate_provider_biome(
    biome_id: str,
    valid_ids: set[str],
    namespace: str,
    column_name: str,
    path: Path,
    line_number: int,
) -> None:
    if not biome_id:
        return
    if not biome_id.startswith(f"{namespace}:"):
        raise ValueError(
            f"{column_name} must use '{namespace}:' namespace "
            f"({path}:{line_number}, value={biome_id!r})"
        )
    if biome_id not in valid_ids:
        raise ValueError(
            f"Unknown {column_name} in batch CSV "
            f"({path}:{line_number}, value={biome_id!r})"
        )


def validate_unique_provider_priorities(row: dict[str, str], path: Path, line_number: int) -> None:
    priority_columns = [
        "BIOMES_O_PLENTY_BIOME_PRIORITY",
        "REGIONS_UNEXPLORED_BIOME_PRIORITY",
        "NATURES_SPIRIT_BIOME_PRIORITY",
    ]
    by_priority: dict[str, str] = {}
    for column in priority_columns:
        value = row.get(column, "").strip()
        if not value:
            continue
        existing = by_priority.get(value)
        if existing is not None:
            raise ValueError(
                f"Duplicate provider biome priority {value!r} in batch CSV "
                f"({path}:{line_number}, columns={existing},{column})"
            )
        by_priority[value] = column


def load_rows(
    batch_dir: Path,
    valid_bop_biome_ids: set[str],
    valid_regions_unexplored_biome_ids: set[str],
    valid_natures_spirit_biome_ids: set[str],
) -> list[MappingRow]:
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
                "BIOMES_O_PLENTY_BIOME_PRIORITY",
                "REGIONS_UNEXPLORED_BIOME",
                "REGIONS_UNEXPLORED_BIOME_PRIORITY",
                "NATURES_SPIRIT_BIOME",
                "NATURES_SPIRIT_BIOME_PRIORITY",
                "MINECRAFT_BIOME",
            }
            if not required.issubset(set(reader.fieldnames or [])):
                missing = sorted(required.difference(set(reader.fieldnames or [])))
                raise ValueError(f"{path} missing columns {missing}")

            for line_number, row in enumerate(reader, start=2):
                color_hex = normalize_color_hex(row["UNIQUE_ECOREGION_COLOR"])
                eco_name = row["ECO_NAME"].strip()
                biome_name = row["BIOME_NAME"].strip()
                realm = row["REALM"].strip()
                bop_biome_id = normalize_optional_biome_id(
                    row["BIOMES_O_PLENTY_BIOME"],
                    "BIOMES_O_PLENTY_BIOME",
                )
                bop_priority = normalize_optional_priority(
                    row["BIOMES_O_PLENTY_BIOME_PRIORITY"],
                    bop_biome_id,
                    "BIOMES_O_PLENTY_BIOME",
                    "BIOMES_O_PLENTY_BIOME_PRIORITY",
                )
                regions_unexplored_biome_id = normalize_optional_biome_id(
                    row["REGIONS_UNEXPLORED_BIOME"],
                    "REGIONS_UNEXPLORED_BIOME",
                )
                regions_unexplored_priority = normalize_optional_priority(
                    row["REGIONS_UNEXPLORED_BIOME_PRIORITY"],
                    regions_unexplored_biome_id,
                    "REGIONS_UNEXPLORED_BIOME",
                    "REGIONS_UNEXPLORED_BIOME_PRIORITY",
                )
                natures_spirit_biome_id = normalize_optional_biome_id(
                    row["NATURES_SPIRIT_BIOME"],
                    "NATURES_SPIRIT_BIOME",
                )
                natures_spirit_priority = normalize_optional_priority(
                    row["NATURES_SPIRIT_BIOME_PRIORITY"],
                    natures_spirit_biome_id,
                    "NATURES_SPIRIT_BIOME",
                    "NATURES_SPIRIT_BIOME_PRIORITY",
                )
                minecraft_biome_id = normalize_biome_id(row["MINECRAFT_BIOME"], "MINECRAFT_BIOME")
                validate_provider_biome(
                    bop_biome_id,
                    valid_bop_biome_ids,
                    "biomesoplenty",
                    "BIOMES_O_PLENTY_BIOME",
                    path,
                    line_number,
                )
                validate_provider_biome(
                    regions_unexplored_biome_id,
                    valid_regions_unexplored_biome_ids,
                    "regions_unexplored",
                    "REGIONS_UNEXPLORED_BIOME",
                    path,
                    line_number,
                )
                validate_provider_biome(
                    natures_spirit_biome_id,
                    valid_natures_spirit_biome_ids,
                    "natures_spirit",
                    "NATURES_SPIRIT_BIOME",
                    path,
                    line_number,
                )
                validate_unique_provider_priorities(
                    {
                        "BIOMES_O_PLENTY_BIOME_PRIORITY": bop_priority,
                        "REGIONS_UNEXPLORED_BIOME_PRIORITY": regions_unexplored_priority,
                        "NATURES_SPIRIT_BIOME_PRIORITY": natures_spirit_priority,
                    },
                    path,
                    line_number,
                )

                mapping = MappingRow(
                    color_hex=color_hex,
                    eco_name=eco_name,
                    biome_name=biome_name,
                    realm=realm,
                    bop_biome_id=bop_biome_id,
                    bop_priority=bop_priority,
                    regions_unexplored_biome_id=regions_unexplored_biome_id,
                    regions_unexplored_priority=regions_unexplored_priority,
                    natures_spirit_biome_id=natures_spirit_biome_id,
                    natures_spirit_priority=natures_spirit_priority,
                    minecraft_biome_id=minecraft_biome_id,
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
                "BIOMES_O_PLENTY_BIOME_PRIORITY",
                "REGIONS_UNEXPLORED_BIOME",
                "REGIONS_UNEXPLORED_BIOME_PRIORITY",
                "NATURES_SPIRIT_BIOME",
                "NATURES_SPIRIT_BIOME_PRIORITY",
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
                    "BIOMES_O_PLENTY_BIOME_PRIORITY": row.bop_priority,
                    "REGIONS_UNEXPLORED_BIOME": row.regions_unexplored_biome_id,
                    "REGIONS_UNEXPLORED_BIOME_PRIORITY": row.regions_unexplored_priority,
                    "NATURES_SPIRIT_BIOME": row.natures_spirit_biome_id,
                    "NATURES_SPIRIT_BIOME_PRIORITY": row.natures_spirit_priority,
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
    parser.add_argument(
        "--regions-unexplored-biomes-csv",
        type=Path,
        default=script_dir / "regions_unexplored_biomes.csv",
        help="CSV containing valid Regions Unexplored biome IDs (column: biome_id).",
    )
    parser.add_argument(
        "--natures-spirit-biomes-csv",
        type=Path,
        default=script_dir / "natures_spirit_biomes.csv",
        help="CSV containing valid Nature's Spirit biome IDs (column: biome_id).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    valid_bop_biome_ids = load_valid_biome_ids(args.bop_biomes_csv, "Biomes O' Plenty")
    valid_regions_unexplored_biome_ids = load_valid_biome_ids(
        args.regions_unexplored_biomes_csv,
        "Regions Unexplored",
    )
    valid_natures_spirit_biome_ids = load_valid_biome_ids(
        args.natures_spirit_biomes_csv,
        "Nature's Spirit",
    )
    rows = load_rows(
        args.batch_dir,
        valid_bop_biome_ids,
        valid_regions_unexplored_biome_ids,
        valid_natures_spirit_biome_ids,
    )
    write_rows(rows, args.out_csv)
    print(f"Wrote {len(rows)} rows to {args.out_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
