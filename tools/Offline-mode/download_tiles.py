#!/usr/bin/env python3
"""Download Terrarium Expanded cache tiles for one selected world zoom."""

from __future__ import annotations

import argparse
from concurrent.futures import CancelledError, FIRST_COMPLETED, ThreadPoolExecutor, wait
from dataclasses import dataclass
from http.client import HTTPException
from io import BytesIO
import math
from pathlib import Path
import sys
import tempfile
import threading
import time
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


DEFAULT_ENDPOINTS = {
    "terrarium": "https://elevation-tiles-prod.s3.amazonaws.com/terrarium",
    "surface_water": "https://storage.googleapis.com/global-surface-water/tiles2021/seasonality",
    "ecoregions": "https://d127t6piqu53ls.cloudfront.net/tiles-reduced",
}
MAX_ATTEMPTS = 3
REQUEST_TIMEOUT = 30
BACKOFF_SECONDS = 1.0
# Match SurfaceWaterCoverageSettings in the mod, including intersecting boundary rows.
WATER_MIN_LATITUDE = -60.0
WATER_MAX_LATITUDE = 77.0
# Measured on 2026-09-22 from the default-source z8 download. Each tuple is
# (PNG count, total PNG bytes, total bytes rounded per file to 4 KiB blocks).
# Water includes only tiles intersecting the runtime's coverage, excluding gaps.
Z8_SIZE_BASELINE = {
    "terrarium": (65_536, 6_443_337_776, 6_577_602_560),
    "surface_water": (36_352, 221_151_172, 326_402_048),
    "ecoregions": (4_096, 24_206_770, 31_621_120),
}


@dataclass(frozen=True)
class TileGrid:
    layer: str
    zoom: int
    axis_count: int
    image_size: int
    base_url: str
    y_start: int = 0
    y_stop: int | None = None

    @property
    def rows(self) -> range:
        return range(self.y_start, self.axis_count if self.y_stop is None else self.y_stop)

    @property
    def count(self) -> int:
        return self.axis_count * len(self.rows)

    @property
    def outside_coverage_count(self) -> int:
        return self.axis_count ** 2 - self.count


@dataclass(frozen=True)
class TileTask:
    grid: TileGrid
    x: int
    y: int
    cache_root: Path

    @property
    def url(self) -> str:
        return f"{self.grid.base_url}/{self.grid.zoom}/{self.x}/{self.y}.png"

    @property
    def path(self) -> Path:
        return self.cache_root / self.grid.layer / str(self.grid.zoom) / str(self.x) / f"{self.y}.png"

    @property
    def marker_path(self) -> Path:
        return self.path.with_suffix(".png.missing")


@dataclass
class DownloadStats:
    downloaded: int = 0
    cached: int = 0
    missing: int = 0
    failed: int = 0

    @property
    def completed(self) -> int:
        return self.downloaded + self.cached + self.missing + self.failed

    def summary(self) -> str:
        return (f"downloaded={self.downloaded:,} cached={self.cached:,} "
                f"missing={self.missing:,} failed={self.failed:,}")


def positive_int(value: str) -> int:
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return number


def endpoint(value: str) -> str:
    value = value.strip().rstrip("/")
    parsed = urlsplit(value)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or parsed.query or parsed.fragment:
        raise argparse.ArgumentTypeError("must be an HTTP(S) base URL without a query or fragment")
    return value


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zoom", type=int, choices=range(8, 13), default=8, help="world zoom (default: 8)")
    parser.add_argument("--output", type=Path, help="directory containing terrarium_expanded/ to copy into the game cache (default: downloads/z<zoom> beside this script)")
    parser.add_argument("--workers", type=positive_int, default=4, help="concurrent downloads (default: 4)")
    parser.add_argument("--dry-run", action="store_true", help="print grids and paths without network requests or writes")
    for flag, layer in (("terrain", "terrarium"), ("surface-water", "surface_water"), ("ecoregions", "ecoregions")):
        parser.add_argument(f"--{flag}-base-url", type=endpoint, default=DEFAULT_ENDPOINTS[layer],
                            help=f"{layer} source base URL")
    return parser.parse_args(argv)


def surface_water_rows(zoom: int) -> range:
    def latitude(row: int) -> float:
        mercator_n = math.pi * (1.0 - 2.0 * row / (1 << zoom))
        return math.degrees(math.atan(math.sinh(mercator_n)))

    # Keep a row whenever any part intersects the coverage, as the runtime does.
    covered = [row for row in range(1 << zoom)
               if not (latitude(row) < WATER_MIN_LATITUDE or latitude(row + 1) > WATER_MAX_LATITUDE)]
    return range(covered[0], covered[-1] + 1)


def build_plan(args: argparse.Namespace) -> list[TileGrid]:
    grids = [TileGrid("terrarium", args.zoom, 1 << args.zoom, 256, args.terrain_base_url)]
    water_rows = surface_water_rows(args.zoom)
    grids.append(TileGrid("surface_water", args.zoom, 1 << args.zoom, 256, args.surface_water_base_url,
                          water_rows.start, water_rows.stop))
    grids.append(TileGrid("ecoregions", 8, 64, 1024, args.ecoregions_base_url))
    return grids


def print_size_warning(grids: Iterable[TileGrid]) -> None:
    png_bytes = disk_bytes = 0.0
    for grid in grids:
        baseline_count, baseline_png_bytes, baseline_disk_bytes = Z8_SIZE_BASELINE[grid.layer]
        png_bytes += grid.count * baseline_png_bytes / baseline_count
        disk_bytes += grid.count * baseline_disk_bytes / baseline_count
    print(f"WARNING: Estimated full download: {png_bytes / 1024 ** 3:,.2f} GiB of PNG data;"
          f" about {disk_bytes / 1024 ** 3:,.2f} GiB on disk (4 KiB blocks).")
    print("Based on measured average tile sizes from the default-source zoom-8 download.")
    print("Higher zooms and custom sources are projections, not guaranteed sizes. Allow extra free space.")
    print("Estimates cover the full dataset, not ZIP size or remaining downloads; cached/missing tiles reduce transfers.", flush=True)


def iter_tasks(grids: Iterable[TileGrid], cache_root: Path) -> Iterable[TileTask]:
    # Do not materialize millions of coordinates or futures at higher zooms.
    for grid in grids:
        for x in range(grid.axis_count):
            for y in grid.rows:
                yield TileTask(grid, x, y, cache_root)


def validate_png(source: Path | BytesIO, size: int) -> None:
    from PIL import Image

    with Image.open(source) as image:
        if image.format != "PNG" or image.size != (size, size):
            raise ValueError(f"expected a {size}x{size} PNG, got {image.format} {image.size}")
        image.verify()
    if isinstance(source, BytesIO):
        source.seek(0)
    with Image.open(source) as image:
        image.load()


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + ".", suffix=".part", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def is_known_missing(task: TileTask) -> bool:
    try:
        fields = dict(line.split("=", 1) for line in task.marker_path.read_text(encoding="utf-8").splitlines() if "=" in line)
        return fields.get("status") in ("404", "410") and fields.get("uri") == task.url
    except (OSError, UnicodeError):
        return False


def fetch_png(task: TileTask, stop: threading.Event) -> bytes | int:
    """Return validated original bytes, or a confirmed missing HTTP status."""
    for attempt in range(MAX_ATTEMPTS):
        if stop.is_set():
            raise CancelledError()
        try:
            request = Request(task.url, headers={"User-Agent": "TerrariumExpanded-OfflineDownloader/1.0"})
            with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
                if response.status != 200:
                    raise OSError(f"unexpected HTTP status {response.status}")
                data = response.read()
            validate_png(BytesIO(data), task.grid.image_size)
            return data
        except HTTPError as error:
            status = error.code
            error.close()
            if status in (404, 410):
                return status
            if status not in (408, 429) and not 500 <= status <= 599:
                raise
            if attempt == MAX_ATTEMPTS - 1:
                raise
        except (OSError, URLError, HTTPException, ValueError, SyntaxError):
            if attempt == MAX_ATTEMPTS - 1:
                raise
        if stop.wait(BACKOFF_SECONDS * (2 ** attempt)):
            raise CancelledError()
    raise AssertionError("unreachable")


def download_tile(task: TileTask, stop: threading.Event) -> str:
    if stop.is_set():
        raise CancelledError()
    # Match the runtime's marker-first lookup. Only trust markers for this URL.
    if is_known_missing(task):
        return "missing"
    task.marker_path.unlink(missing_ok=True)
    if task.path.exists():
        try:
            validate_png(task.path, task.grid.image_size)
            return "cached"
        except (OSError, ValueError, SyntaxError):
            pass
    result = fetch_png(task, stop)
    if stop.is_set():
        raise CancelledError()
    if isinstance(result, int):
        body = f"status={result}\nuri={task.url}\n".encode("utf-8")
        atomic_write(task.marker_path, body)
        task.path.unlink(missing_ok=True)
        return "missing"
    atomic_write(task.path, result)
    task.marker_path.unlink(missing_ok=True)
    return "downloaded"


def run_downloads(tasks: Iterable[TileTask], total: int, workers: int) -> DownloadStats:
    stats = DownloadStats()
    stop = threading.Event()
    executor = ThreadPoolExecutor(max_workers=workers)
    pending = {}
    source = iter(tasks)
    last_progress = time.monotonic()

    def fill_queue() -> None:
        while len(pending) < workers * 2:
            task = next(source, None)
            if task is None:
                break
            pending[executor.submit(download_tile, task, stop)] = task

    try:
        fill_queue()
        while pending:
            completed, _ = wait(pending, timeout=1, return_when=FIRST_COMPLETED)
            for future in completed:
                task = pending.pop(future)
                try:
                    status = future.result()
                    setattr(stats, status, getattr(stats, status) + 1)
                except Exception as error:
                    stats.failed += 1
                    print(f"FAILED {task.url}: {error}", file=sys.stderr, flush=True)
            fill_queue()
            if time.monotonic() - last_progress >= 5:
                print(f"{stats.completed:,}/{total:,}: {stats.summary()}", flush=True)
                last_progress = time.monotonic()
    finally:
        stop.set()
        for future in pending:
            future.cancel()
        executor.shutdown(wait=True, cancel_futures=True)
        print(f"Finished {stats.completed:,}/{total:,}: {stats.summary()}", flush=True)
    return stats


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    output = args.output if args.output is not None else Path(__file__).resolve().parent / "downloads" / f"z{args.zoom}"
    cache_root = output.resolve() / "terrarium_expanded"
    grids = build_plan(args)
    total = sum(grid.count for grid in grids)
    print(f"World zoom: {args.zoom}\nCache directory: {cache_root}")
    for grid in grids:
        print(f"  {grid.layer} z={grid.zoom}: {grid.count:,} locations"
              f" ({grid.axis_count} columns, rows {grid.rows.start}-{grid.rows.stop - 1}) from {grid.base_url}")
        if grid.outside_coverage_count:
            print(f"    Outside coverage: {grid.outside_coverage_count:,} water locations skipped (60 S to 77 N coverage).")
    print(f"Total: {total:,} tile locations; workers: {args.workers}", flush=True)
    print_size_warning(grids)
    if args.dry_run:
        return 0
    try:
        confirmed = input(f"Download zoom {args.zoom} tiles to {cache_root}? [y/N] ").strip().lower()
    except EOFError:
        print("No confirmation received. Download cancelled.", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nDownload cancelled.", file=sys.stderr)
        return 130
    if confirmed not in ("y", "yes"):
        print("Download cancelled.")
        return 0
    try:
        import PIL.Image  # Check the dependency before starting worker threads.
    except ImportError:
        print("Pillow is required. Run: python -m pip install Pillow", file=sys.stderr)
        return 1
    try:
        stats = run_downloads(iter_tasks(grids, cache_root), total, args.workers)
    except KeyboardInterrupt:
        print("Interrupted. Run the same command to resume.", file=sys.stderr)
        return 130
    if stats.failed:
        print("Download incomplete. Run the same command to retry failed tiles.", file=sys.stderr)
        return 1
    print(f"Download complete. Copy {cache_root} into your Minecraft instance's cache directory.")
    print("Confirmed missing tiles retain the mod's existing fallback behavior.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
