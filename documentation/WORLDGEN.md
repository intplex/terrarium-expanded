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
- `sea_level`
- `below_sea_height_mode` (`even_scale`, `sea_level_detail`, `high_elevation_detail`, `compressed_middle_heights`)
- `above_sea_height_mode` (`even_scale`, `sea_level_detail`, `high_elevation_detail`, `compressed_middle_heights`)
- `terrain_base_url`
- `biomes_base_url`
- `surface_water_base_url`
- `terrain_fixes` (currently only `none`)
- `world_border`
- `spawn_latitude`
- `spawn_longitude`
- `biome_integration` (`auto`, `vanilla`, `biomes_o_plenty`, `regions_unexplored`, `natures_spirit`; legacy `expanded` reads as `biomes_o_plenty`)
- `generation` toggles:
  - `caves`
  - `canyons`
  - `extra_underground`
  - `aquifers`
  - `lava_aquifers`
  - `villages`

The world-creation editor presents `aquifers` and `lava_aquifers` as one `Cave fluids` control with three states: `Off`, `Water only`, or `Water + lava`. The two fields remain in the serialized profile for compatibility with existing worlds and datapacks. Surface water/lava lake features are separate from this control and continue to follow the selected biomes' normal feature lists.

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
- shape-only changes (`max_mountain_y`, `ocean_floor_y`, `sea_level`, height modes) still replace runtime context but reuse service instances
- supplemental terrain source services are instantiated only when required by:
  - GeoJSON bathymetry source-zoom overrides for the active world zoom
  - ocean-zero bathymetry recovery source zooms `10` and `8` (world zoom `>= 11`)

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
6. At zoom 11+, optionally recover ocean bathymetry from lower-zoom Terrarium tiles when gates pass:
   - sampled meters are exactly `0.0`
   - ecoregion pixel is no-data (`#000000`)
   - sampled surface-water pixel is water
   - source zoom `10` is tried first, then source zoom `8` if zoom `10` is unavailable or still resolves to `0.0`
   - repeated source-pixel neighborhoods are memoized within each chunk snapshot; known all-zero source neighborhoods are skipped on later samples in the same snapshot
7. Convert meters to terrain Y with `EarthGenConfig.mapMetersToTerrainY` (uses active `max_mountain_y` / `ocean_floor_y` / `sea_level` plus independent above/below height modes).
8. Build per-chunk snapshots in `TerrainChunkSnapshotBuilder`:
   - sample grid: `40 x 40` (16x16 chunk plus relief margin)
   - spike suppression via `TerrainMetricsKernel.suppressIsolatedSpikes`
   - channel metrics via `TerrainMetricsKernel.computeMetricsAt`
9. Inland-water analysis (`InlandWaterAnalysis`) computes water mask/kind/surface Y/effective solid top.
10. Post-noise envelope enforcement (`EarthTerrainEnvelopePostProcessor`) removes any vanilla aquifer barriers or other noise-stage blocks above the exact mapped Earth solid top, restoring ocean fluid below sea level and air at/above sea level.
11. Post-noise inland-water fill (`InlandWaterChunkPostProcessor`) writes water blocks for inland columns.
12. After AIR carvers finish, the terrain envelope and inland surface water are reasserted, then `EarthSurfaceWaterCavePostProcessor` floods only air components that are hydraulically connected to an ocean, mapped lake, or mapped river. Propagation has a per-column ceiling taken from that column's mapped water surface, or its mapped terrain surface outside the water mask. This preserves the source heightmap instead of spreading the highest nearby lake sample sideways. Air above the ceiling, sealed caves, solids, lava, and other fluids are preserved.

Note: if any in-bounds sample in a snapshot lacks usable surface-water data, inland-water fill is disabled for that snapshot.

## Biome Selection Pipeline

`EcoregionBiomeSource#getNoiseBiome` does:

1. If cave carving is enabled (`generation.caves` or `generation.extra_underground`) and the queried Y is below the Earth terrain surface by the vanilla cave-biome depth threshold, ask the registry-provided `minecraft:overworld` multi-noise biome source for the current `(quartX, quartY, quartZ)`.
2. If that delegate returns a biome in `#terrarium_expanded:is_underground` or `#c:is_cave`, use it directly.
3. Otherwise sample ecoregion color from reduced tiles (source zoom 8).
4. Resolve color to biome from `color_biome_map.csv` through `EcoregionBiomeMappings` (mode depends on `biome_integration`, provider priority, and mod availability).
5. If color is unmapped/unavailable, fall back to ocean biome selection using:
   - terrain-derived depth tier
   - WOA23 SST temperature tiering
6. Apply inland-water override:
   - inland water -> `river` or `frozen_river` (based on `coldEnoughToSnow`)
   - otherwise keep the resolved biome

The ecoregion CSV is surface/ocean mapping only. Underground biome placement comes from the loaded Minecraft/modded overworld biome generator and is accepted only through cave biome tags. Mods can participate by adding cave biomes to the overworld multi-noise parameter list and tagging them under `#c:is_cave` or `#terrarium_expanded:is_underground`. It is also gated by cave-carver toggles and by a surface-relative depth check so cave biomes do not replace exposed terrain. Terrarium derives that depth check and any bottom-biome plateau from the registered cave-biome depth parameter ranges, so bottom biomes such as `minecraft:deep_dark` are not compressed into a near-zero-height band. Underground biome IDs still do not carve terrain by themselves; cave shape comes from vanilla cave density/carvers clipped by the Terrarium Earth surface.

## Density Wiring

`terrarium_expanded:earth_overworld` is generated at build time by merging vanilla `overworld.json` with `src/main/templates/worldgen/noise_settings/earth_overworld.patch.json`.

Patch routing:

- `continents` -> `terrarium_expanded:terrain_continentalness`
- `erosion` -> `terrarium_expanded:terrain_erosion`
- `depth` -> `terrarium_expanded:terrain_depth` (surface-relative biome depth, including vanilla bottom-biome depth anchoring)
- `ridges` -> `terrarium_expanded:terrain_weirdness`
- `preliminary_surface_level` -> `terrarium_expanded:terrain_envelope`
- `final_density` -> `terrarium_expanded:earth_surface_caves`, wrapping vanilla `preliminary_surface_level.density` and vanilla `final_density`

Vanilla noise aquifers can intentionally return solid barrier material in negative-density space between fluid bodies. Because that behavior is evaluated after the Earth density function, the envelope pass is the final authority on terrain height: cave/aquifer output may alter blocks at or below the mapped solid top, but never above it. The invariant is checked once after noise generation and again after AIR carvers, since a canyon or modded carver can remove the mapped seabed later in the pipeline.

Settings:

- sea level: profile `sea_level` (default `63`)
- `ore_veins_enabled`: always `true`
- `aquifers_enabled`: patch default `false`, then runtime-adjusted from Earth `generation.aquifers`

## Generation Toggles (Actual Effects)

`generation` toggles are enforced in mixins:

- `caves`, `canyons`, `extra_underground`:
  - filter AIR carvers in `NoiseBasedChunkGenerator.applyCarvers`
  - vanilla and modded canyon/ravine carvers follow `canyons`
  - `cave_extra_underground` follows `extra_underground`
  - all other AIR carvers, including unregistered and otherwise-unrecognized modded carvers, follow `caves` so the default off state cannot leak caves
- `caves`, `extra_underground`:
  - allow tagged underground biome selection below the local Earth terrain surface
- `caves`:
  - vanilla noise caves and biome-provided vanilla/modded carvers are clipped by the Earth terrain surface
- after AIR carving, regardless of aquifer mode:
  - an air cave or canyon that actually opens into an ocean or mapped inland-water body is filled with source water up to that body's surface
  - every surface-water column retains its own mapped height; a higher sample cannot create a raised sheet over lower lake columns or dry shoreline
  - connectivity, rather than the column's biome, controls this repair, so a sealed dry cave is not flooded merely because it lies beneath water
  - fluid propagation is bounded to the generating chunk; repaired boundary cells are scheduled for Minecraft's normal cross-chunk fluid propagation
- when `aquifers=false`:
  - cave/carver voids below land remain dry
  - cave/carver voids below submerged Earth columns receive the configured ocean fluid up to sea level, preventing air caves and suspended water when a cave breaches the ocean floor
- `aquifers`:
  - switches Minecraft's underground fluid-distribution system for Earth chunks; it changes cave fluids, not cave geometry or surface lake features
- `lava_aquifers`:
  - when aquifers are enabled and `lava_aquifers=false`, force a water-only global fluid picker and zero the noise router's separate late-stage lava conversion
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
