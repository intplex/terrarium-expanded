# Offline tile downloads

Download Terrarium Expanded tiles for offline use.

## Ready-made download (z=8)

[Download the zoom-8 offline cache (.7z, 6.58 GB)](http://d127t6piqu53ls.cloudfront.net/downloads/z8-offline-cache.7z), including terrain, surface-water, and ecoregion tiles.

Close Minecraft and extract the archive into `path/to/minecraft/cache/` so that it contains `terrarium_expanded/`. Select **zoom 8** for your Earth world. Python is not required when using this archive.

## Usage

To download tiles yourself, use Python 3.10+ and Pillow. Run from the repository root:

```powershell
python -m pip install -r tools/Offline-mode/requirements.txt
python tools/Offline-mode/download_tiles.py
```

Defaults to **zoom 8**, four workers, and output in `tools/Offline-mode/downloads/z8/terrarium_expanded/`. Every download run shows a size estimate and waits for `y` or `yes` to continue. Zoom 8 is estimated at **6.23 GiB of PNG data / 6.46 GiB on disk**, based on measured per-layer tile sizes and 4 KiB disk blocks. Estimates cover the full dataset, including on resumed runs; higher zooms and custom sources are projections. Allow extra free space. ZIP sizes will differ.

Preview without downloading or choose a different zoom and output directory:

```powershell
python tools/Offline-mode/download_tiles.py --dry-run
python tools/Offline-mode/download_tiles.py --zoom 10 --output "path/to/downloads/z10" --workers 4
```

Supported zooms are **8–12**. Each run includes terrain and surface-water tiles at the selected zoom, plus the fixed zoom-8 reduced ecoregion tiles. Water tiles wholly outside **60°S–77°N** are skipped, matching the mod; intersecting boundary rows are included. Zoom 8 requests **106,240 tile locations**, skipping **28,928** polar water locations. Higher zooms roughly quadruple the terrain and water counts. Ocean temperature data and biome mappings already ship with the mod.

## Install and share

Close Minecraft and copy the generated **`terrarium_expanded` folder** into **`path/to/minecraft/cache/`**, merging existing folders. For a zoom-8 download, the result is:

```text
path/to/minecraft/cache/terrarium_expanded/
    terrarium/8/<x>/<y>.png
    surface_water/8/<x>/<y>.png
    ecoregions/8/<x>/<y>.png
```

Use the same world zoom as the download. Missing markers outside water coverage are unnecessary. Keep `.png.missing` markers for gaps within coverage, including boundary rows, to prevent repeat requests; removing them does not supply the missing data.

**Zoom 8 needs no other zooms.** For full offline coverage with default recovery rules, worlds at zoom 9–10 also need terrain at zoom 8; worlds at zoom 11–12 need terrain at zooms 8 and 10. Download those zooms separately and merge their `terrarium` folders into the same cache. Custom recovery rules may require additional zooms.

To share a download, manually create a ZIP with `terrarium_expanded/` at its root. Users extract it into `path/to/minecraft/cache/`. Include missing markers and exclude `.part` files; the script does not create ZIPs.

## Resume and custom sources

Rerun the same command to resume. Valid cached tiles and known missing markers are skipped; corrupt tiles are downloaded again. Transient failures receive three attempts. Unresolved failures return exit code 1; Ctrl+C returns 130. Active requests may take up to their timeout to stop.

HTTP 404/410 responses create `.png.missing` markers, preserving the mod's fallback behavior. Delete a marker and rerun to recheck that tile. Leftover `.part` files can be deleted while the script is stopped.

Custom sources use `--terrain-base-url`, `--surface-water-base-url`, and `--ecoregions-base-url`. Supply HTTP(S) base URLs matching the world's settings and the default tile layouts, without `/z/x/y.png`, query strings, or fragments. Use a separate output directory when changing sources, since valid cached tiles are reused. The script does not read Minecraft configuration files.
