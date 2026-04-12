#!/usr/bin/env python3
"""Build stitched z=8 ecoregion tiles for reduced request fanout."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

SOURCE_ZOOM = 8
SOURCE_TILE_SIZE = 256
SOURCE_TILES_PER_AXIS = 1 << SOURCE_ZOOM
DEFAULT_GROUP_SIZE = 4


@dataclass(frozen=True)
class BuildStats:
    tile_count: int
    total_bytes: int
    max_tile_bytes: int


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description=(
            "Stitch existing z=8 ecoregion tiles into larger reduced tiles "
            "to reduce runtime HTTP request volume."
        )
    )
    parser.add_argument(
        "--in-root",
        type=Path,
        default=script_dir / "tiles" / str(SOURCE_ZOOM),
        help="Input root containing source tiles in x/y.png layout (default: tools/Ecoregions2017/tiles/8).",
    )
    parser.add_argument(
        "--out-root",
        type=Path,
        default=script_dir / "tiles-reduced" / str(SOURCE_ZOOM),
        help="Output root for stitched tiles in x/y.png layout (default: tools/Ecoregions2017/tiles-reduced/8).",
    )
    parser.add_argument(
        "--group-size",
        type=int,
        default=DEFAULT_GROUP_SIZE,
        help="Number of source tiles per axis stitched into one output tile (default: 4).",
    )
    return parser.parse_args()


def validate_group_size(group_size: int) -> int:
    if group_size <= 0:
        raise ValueError("--group-size must be > 0")
    if SOURCE_TILES_PER_AXIS % group_size != 0:
        raise ValueError(
            f"--group-size {group_size} must divide source tiles-per-axis {SOURCE_TILES_PER_AXIS}"
        )
    return SOURCE_TILES_PER_AXIS // group_size


def load_source_tile(path: Path) -> Image.Image:
    if not path.exists():
        raise FileNotFoundError(f"Missing source tile: {path}")
    with Image.open(path) as image:
        if image.size != (SOURCE_TILE_SIZE, SOURCE_TILE_SIZE):
            raise ValueError(
                f"Unexpected source tile dimensions for {path}: "
                f"{image.size[0]}x{image.size[1]} (expected {SOURCE_TILE_SIZE}x{SOURCE_TILE_SIZE})"
            )
        if image.mode != "RGB":
            image = image.convert("RGB")
        return image.copy()


def build_tiles(in_root: Path, out_root: Path, group_size: int) -> BuildStats:
    reduced_tiles_per_axis = validate_group_size(group_size)
    reduced_tile_size = SOURCE_TILE_SIZE * group_size
    tile_count = 0
    total_bytes = 0
    max_tile_bytes = 0

    for reduced_x in range(reduced_tiles_per_axis):
        for reduced_y in range(reduced_tiles_per_axis):
            stitched = Image.new("RGB", (reduced_tile_size, reduced_tile_size))
            for dx in range(group_size):
                for dy in range(group_size):
                    source_x = reduced_x * group_size + dx
                    source_y = reduced_y * group_size + dy
                    source_path = in_root / str(source_x) / f"{source_y}.png"
                    source_tile = load_source_tile(source_path)
                    stitched.paste(
                        source_tile,
                        (dx * SOURCE_TILE_SIZE, dy * SOURCE_TILE_SIZE),
                    )

            out_path = out_root / str(reduced_x) / f"{reduced_y}.png"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            stitched.save(out_path, format="PNG", optimize=True, compress_level=9)
            file_size = out_path.stat().st_size
            tile_count += 1
            total_bytes += file_size
            max_tile_bytes = max(max_tile_bytes, file_size)

        if (reduced_x + 1) % 8 == 0 or reduced_x + 1 == reduced_tiles_per_axis:
            print(
                f"[progress] columns={reduced_x + 1}/{reduced_tiles_per_axis} "
                f"tiles={tile_count}/{reduced_tiles_per_axis * reduced_tiles_per_axis}"
            )

    return BuildStats(tile_count=tile_count, total_bytes=total_bytes, max_tile_bytes=max_tile_bytes)


def main() -> int:
    args = parse_args()
    stats = build_tiles(args.in_root, args.out_root, args.group_size)
    average_bytes = (stats.total_bytes / stats.tile_count) if stats.tile_count else 0.0
    print(f"Wrote {stats.tile_count} reduced tiles to {args.out_root}")
    print(f"Total bytes: {stats.total_bytes}")
    print(f"Average bytes per tile: {average_bytes:.2f}")
    print(f"Max tile bytes: {stats.max_tile_bytes}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
