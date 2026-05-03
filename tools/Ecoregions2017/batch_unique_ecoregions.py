#!/usr/bin/env python3
"""Split ecoregion-unique-colors.csv into 100-row batches with biome mapping columns."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


def chunked(iterable: list[dict[str, str]], size: int) -> list[list[dict[str, str]]]:
    return [iterable[i : i + size] for i in range(0, len(iterable), size)]


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Produce 100-row batches of tools/Ecoregions2017/ecoregions-unique-colors.csv "
            "and add blank provider biome/priority columns so you can fill them manually."
        )
    )
    parser.add_argument(
        "--csv",
        type=Path,
        default=Path("tools/Ecoregions2017/ecoregions-unique-colors.csv"),
        help="Input CSV to split.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=100,
        help="Number of rows per batch file.",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("tools/Ecoregions2017/batches"),
        help="Directory where batch CSV files will be written.",
    )

    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)

    with args.csv.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        fieldnames = list(reader.fieldnames or [])
        for column in [
            "BIOMES_O_PLENTY_BIOME",
            "BIOMES_O_PLENTY_BIOME_PRIORITY",
            "REGIONS_UNEXPLORED_BIOME",
            "REGIONS_UNEXPLORED_BIOME_PRIORITY",
            "NATURES_SPIRIT_BIOME",
            "NATURES_SPIRIT_BIOME_PRIORITY",
            "MINECRAFT_BIOME",
        ]:
            if column not in fieldnames:
                fieldnames.append(column)
        rows = list(reader)

    for idx, chunk in enumerate(chunked(rows, args.batch_size), start=1):
        batch_path = args.out_dir / f"unique_ecoregions_batch_{idx:03}.csv"
        with batch_path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames)
            writer.writeheader()
            for row in chunk:
                for column in fieldnames:
                    if column not in row:
                        row[column] = ""
                writer.writerow(row)
        print(f"Wrote {batch_path} ({len(chunk)} rows)")

    print(f"Generated {idx} batch files in {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
