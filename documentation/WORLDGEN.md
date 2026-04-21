# Terrarium Expanded Worldgen (Current)

This document describes the worldgen currently implemented in `common`, `fabric`, and `neoforge`.

## Overview

Terrarium Expanded builds Earth-like terrain/biomes from four runtime data layers:

1. Terrarium elevation PNG tiles (AWS Terrarium)
2. Reduced WWF ecoregion PNG tiles (color -> biome lookup)
3. Global surface-water seasonality PNG tiles (inland-water and bathymetry gating)
4. WOA23 mean annual SST grid (ocean biome temperature tiering)

The Earth preset (`data/terrarium_expanded/worldgen/world_preset/earth.json`) uses:

- biome source: `terrarium_expanded:ecoregion_tiles`
- noise settings: `terrarium_expanded:earth_overworld`
- end/nether generators: vanilla

## Earth Preset Fields

`EcoregionBiomeSource` + `EarthGenerationProfile` currently carry:

- `zoom`
- `max_mountain_y`
- `ocean_floor_y`
- `terrain_base_url`
- `biomes_base_url`
- `surface_water_base_url`
- `terrain_fixes` (currently only `none`)
- `world_border`
- `spawn_latitude`
- `spawn_longitude`
- `biome_integration` (`auto`, `vanilla`, `expanded`)
- `generation` toggles:
  - `caves`
  - `canyons`
  - `extra_underground`
  - `aquifers`
  - `lava_aquifers`
  - `villages`

## Runtime Architecture

`TerrainServices` owns a runtime `EarthRuntimeContext`:

- active `EarthGenerationProfile`
- active tile services (`terrain`, `recovery`, `ecoregion`, `surface-water`)
- `TerrainService.RuntimeState` caches and dedupe sets
- startup-loaded runtime performance config from `<gameDir>/config/terrarium-expanded.properties`
- startup-loaded bathymetry source-zoom override rules from bundled `bad_tile_recovery.geojson`, optionally merged with `<gameDir>/config/bad_tile_recovery.geojson`

`TerrainServices.runtimeGeneration()` increments whenever the context is replaced; thread-local hot-path caches (`EarthSamplingFacade` / `EcoregionBiomeSource`) invalidate against this generation counter.
Thread-local sampling caches are also idle-cleared using `memory.local_idle_seconds`.

### Service rebuild rules

- terrain URL, ecoregion URL, zoom, or surface-water URL change: rebuild tile services under a shared tile-IO executor
- shape-only changes (`max_mountain_y`, `ocean_floor_y`) still replace runtime context but reuse service instances
- supplemental terrain source services are instantiated only when required by:
  - GeoJSON bathymetry source-zoom overrides for the active world zoom
  - zoom-10 ocean recovery (world zoom `>= 11`)

### Cache lifecycle

Terrain/runtime caches are cleared on:

- runtime context transitions (`syncEarthProfile` / `syncEarthSettings`)
- explicit cache clear (`TerrainServices.clearRuntimeCaches()`, `TerrainService.clearCaches()`)
- shutdown (`TerrainServices.shutdown()`)
- idle TTL expiry (`memory.snapshot_ttl_seconds` and `memory.tile_ttl_seconds`, where `0` disables TTL)

## Core Terrain Pipeline

1. Project `(blockX, blockZ)` to tile/pixel coordinates via `TileProjection` / `EarthGenConfig`.
2. Sample Terrarium meters in `EarthSamplingFacade`.
3. Apply seam correction (`TerrariumSeamPatch.patchedPixelX`) for zoom 11+ around the dateline seam.
4. If the current target tile matches a GeoJSON bathymetry override, resample from a lower source zoom using Mercator-aware region rasterization (bathymetry-only: applies only when sampled meters are `<= 0.0`).
5. Optionally sample surface-water data (needed for inland-water analysis and/or bathymetry recovery).
6. At zoom 11+, optionally recover ocean bathymetry from zoom-10 Terrarium tiles when gates pass:
   - sampled meters are exactly `0.0`
   - ecoregion pixel is no-data (`#000000`)
   - sampled surface-water pixel is water
7. Convert meters to terrain Y with `EarthGenConfig.mapMetersToTerrainY` (uses active `max_mountain_y` / `ocean_floor_y`).
8. Build per-chunk snapshots in `TerrainChunkSnapshotBuilder`:
   - sample grid: `40 x 40` (16x16 chunk plus relief margin)
   - spike suppression via `TerrainMetricsKernel.suppressIsolatedSpikes`
   - channel metrics via `TerrainMetricsKernel.computeMetricsAt`
9. Inland-water analysis (`InlandWaterAnalysis`) computes water mask/kind/surface Y/effective solid top.
10. Post-noise fill (`InlandWaterChunkPostProcessor`) writes water blocks for inland columns.

Note: if any in-bounds sample in a snapshot lacks usable surface-water data, inland-water fill is disabled for that snapshot.

## Biome Selection Pipeline

`EcoregionBiomeSource#getNoiseBiome` does:

1. Sample ecoregion color from reduced tiles (source zoom 8).
2. Resolve color to biome from `color_biome_map.csv` through `EcoregionBiomeMappings` (mode depends on `biome_integration` and mod availability).
3. If color is unmapped/unavailable, fall back to ocean biome selection using:
   - terrain-derived depth tier
   - WOA23 SST temperature tiering
4. Apply inland-water override:
   - inland water -> `river` or `frozen_river` (based on `coldEnoughToSnow`)
   - otherwise keep the resolved biome

## Density Wiring

`terrarium_expanded:earth_overworld` is generated at build time by merging vanilla `overworld.json` with `src/main/templates/worldgen/noise_settings/earth_overworld.patch.json`.

Patch routing:

- `continents` -> `terrarium_expanded:terrain_continentalness`
- `erosion` -> `terrarium_expanded:terrain_erosion`
- `depth` -> `terrarium_expanded:terrain_depth`
- `ridges` -> `terrarium_expanded:terrain_weirdness`
- `preliminary_surface_level` -> `terrarium_expanded:terrain_envelope`
- `initial_density_without_jaggedness` -> `terrarium_expanded:terrain_envelope`
- `final_density` -> `terrarium_expanded:terrain_envelope`

Settings:

- sea level: `63`
- `ore_veins_enabled`: always `true`
- `aquifers_enabled`: patch default `false`, then runtime-adjusted from Earth `generation.aquifers`

## Generation Toggles (Actual Effects)

`generation` toggles are enforced in mixins:

- `caves`, `canyons`, `extra_underground`:
  - filter AIR carvers (`cave`, `canyon`, `cave_extra_underground`) in `NoiseBasedChunkGenerator.applyCarvers`
- `caves`:
  - when enabled and `aquifers=false`, use boundary-aware dry cave carving:
    - cave interiors default to air
    - cave cells directly adjacent to existing fluids keep fluid to avoid dry pockets in water/lava bodies
- `aquifers`:
  - switches `NoiseGeneratorSettings.aquifersEnabled` for Earth chunks
- `lava_aquifers`:
  - when aquifers are enabled and `lava_aquifers=false`, force water-only aquifer fluid picker
  - when `lava_aquifers=false`, skip vanilla lava lake features (`lake_lava_underground` and `lake_lava_surface`)
- `villages`:
  - blocks structures whose path starts with `village_` in `ChunkGenerator.tryGenerateStructure`

Non-village structures are left unchanged by this toggle layer.

## Tile Services

The runtime uses four services:

- `TerrariumTileService` (active world zoom)
- `TerrariumTileService` recovery instance (fixed zoom 10)
- `EcoregionTileService` (reduced ecoregion tiles)
- `SurfaceWaterTileService` (surface-water tiles at world zoom)

All are backed by `RemotePngTileStore` and share:

- weighted in-memory cache (byte budget) + idle TTL eviction
- disk cache
- in-flight request dedupe
- neighbor prefetch
- retrying HTTP fetch

All three dataset base URLs (`terrain`, `biomes`, `surface_water`) are preset-configurable.

## Spawn and Border Behavior

`EarthSpawnManager.forceSpawnFromPreset(...)` runs during server startup:

- spawn point is derived from preset `spawn_latitude` / `spawn_longitude` at current zoom
- if `world_border=true`, a square border is applied around `(0,0)` with a 32-chunk inset from map edges

## Platform Hooks

Both Fabric and NeoForge register:

- `ecoregion_tiles` biome source codec
- terrain density-function codecs
- `/tplatlong` command
- startup spawn forcing
- per-tick worldgen diagnostics updates
- `TerrainServices.shutdown()` on server stop

## Java Runtime Requirement

Mod initialization enforces Java 21+.
