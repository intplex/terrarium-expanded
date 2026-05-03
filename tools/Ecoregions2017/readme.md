# Ecoregions 2017 Sources

Go to (https://ecoregions.appspot.com/)[https://ecoregions.appspot.com/] then click on "About" then you should see "Shapefile (150mb zip) Licensed under CC-BY 4.0" which includes a link to download the Ecoregions 2017 shapefile and metadata.

Extract that zip into `tools/Ecoregions2017/shapefiles/` so these files exist:
- `Ecoregions2017.shp`
- `Ecoregions2017.shx`
- `Ecoregions2017.dbf`
- `Ecoregions2017.prj`

## Build Metadata CSV

Generate `ecoregions-unique-colors.csv` from the shapefile (one row per `ECO_ID` plus deterministic `UNIQUE_ECOREGION_COLOR`):

```powershell
python tools/Ecoregions2017/build_ecoregions_csv.py
```

Default output:
- `tools/Ecoregions2017/ecoregions-unique-colors.csv`

## Build Runtime Color->Biome Mapping

After filling provider biome columns, their priority columns, and `MINECRAFT_BIOME` in each batch CSV, generate the runtime mapping resource consumed by the mod:

- `BIOMES_O_PLENTY_BIOME` + `BIOMES_O_PLENTY_BIOME_PRIORITY`
- `REGIONS_UNEXPLORED_BIOME` + `REGIONS_UNEXPLORED_BIOME_PRIORITY`
- `NATURES_SPIRIT_BIOME` + `NATURES_SPIRIT_BIOME_PRIORITY`
- `MINECRAFT_BIOME`

Lower priority numbers win in `auto` mode when multiple loaded providers have mappings for the same ecoregion. Provider priority values must be unique within each row.

```powershell
python tools/Ecoregions2017/build_runtime_biome_mapping.py
```

Default output:
- `common/src/main/resources/data/terrarium_expanded/ecoregions/color_biome_map.csv`

## Rasterizing Ecoregion Tiles

Use `rasterize_ecoregions_tiles.py` to produce z/x/y PNG tiles from shapefile geometry + `ecoregions-unique-colors.csv`.

Note: for now, zoom `8` is the highest-quality/maximum zoom level available for the ecoregion dataset used by this project.

Dependencies:
- `geopandas`
- `shapely`
- `rasterio`
- `pyproj`
- `Pillow`

Default run:

```powershell
python tools/Ecoregions2017/rasterize_ecoregions_tiles.py
```

Default outputs:
- Tiles: `tools/Ecoregions2017/tiles/8/{x}/{y}.png` (full z=8 grid)

Supported CLI args:
- `--shp <path>`
- `--csv <path>`
- `--out-root <path>`
- `--zoom <int>`

## Build Reduced Ecoregion Tiles

Use `build_reduced_ecoregion_tiles.py` to stitch existing z=8 `256x256` tiles into larger `1024x1024` reduced tiles (4x4 source tiles per output tile).

This reduces runtime HTTP request count by serving fewer, larger ecoregion images while preserving exact per-pixel colors.

Default run:

```powershell
python tools/Ecoregions2017/build_reduced_ecoregion_tiles.py
```

Default outputs:
- Reduced tiles: `tools/Ecoregions2017/tiles-reduced/8/{x}/{y}.png`
- Output grid: `64x64` (`4096` files total)

Supported CLI args:
- `--in-root <path>`
- `--out-root <path>`
- `--group-size <int>` (default `4`)

