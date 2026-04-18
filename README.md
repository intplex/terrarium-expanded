# Terrarium Expanded

Bring the Earth into Minecraft with real-world terrain, biome-driven landcover, and configurable world scale.

> [!IMPORTANT]
> **Requirements**
> - Minecraft `1.21.1`
> - Java `21+`
> - A live internet connection during world generation and exploration
>
> **Terrarium Expanded fetches three live remote data sources:**
> - Terrain tiles
> - Surface-water tiles
> - Ecoregion tiles
>
> If those services are unavailable or blocked, world generation quality and completeness will degrade.

## What The Mod Does

Terrarium Expanded generates an Earth-shaped overworld from real map data instead of vanilla noise alone.

- Terrain comes from AWS Terrarium elevation tiles.
- Land biomes come from reduced WWF ecoregion tiles.
- Inland water uses global surface-water seasonality data.
- Ocean biome temperature is tiered using bundled WOA23 sea-surface temperature data.

You can also choose different zoom levels, which change the playable world's total size and the approximate real-world distance represented by each block.

## Screenshot Gallery

Primary gallery below uses the vanilla biome integration at `z=10`.

### San Francisco

![San Francisco at z=10](documentation/vanilla%20%28z=10%29/1.%20San%20Francisco.png)

### Mt. Fuji

![Mt. Fuji at z=10](documentation/vanilla%20%28z=10%29/3.%20Mt.%20Fuji.png)

### Strait of Gibraltar

![Strait of Gibraltar at z=10](documentation/vanilla%20%28z=10%29/7.%20Straight%20of%20Gibraltar.png)

### Montreal

![Montreal at z=10](documentation/vanilla%20%28z=10%29/8.%20Montreal.png)

### Grand Canyon

![Grand Canyon at z=10](documentation/vanilla%20%28z=10%29/9.%20Grand%20Canyon.png)

More screenshots and alternate biome-integration galleries are indexed in [documentation/readme.md](documentation/readme.md).

## World Size / Zoom Levels

Higher zoom means a much larger playable world with finer real-world detail, but also heavier generation cost and more remote tile fetches.

| Zoom | World Size (blocks) | Approx. Scale | Guidance |
| --- | --- | --- | --- |
| `8` | `65,536 x 65,536` | `~611.50 m/block` | Broad regional scale, fastest option |
| `9` | `131,072 x 131,072` | `~305.75 m/block` | Large world, lighter runtime cost |
| `10` | `262,144 x 262,144` | `~152.87 m/block` | Good default balance for exploration |
| `11` | `524,288 x 524,288` | `~76.44 m/block` | Finer terrain detail, heavier generation |
| `12` | `1,048,576 x 1,048,576` | `~38.22 m/block` | Maximum scale and detail, slowest uncached exploration |

## Requirements

- Minecraft `1.21.1`
- Java `21+`
- Internet access while generating or exploring Earth worlds

The mod depends on live requests for:

- terrain tiles
- surface-water tiles
- ecoregion tiles

These datasets are configurable in the Earth preset editor, but the default experience assumes the public tile services are reachable.

## Performance Expectations

Performance can be poor when the game has to generate many chunks quickly or enter uncached regions for the first time.

- Generating many new chunks in a short period may be slow.
- Entering uncached regions may stutter while remote data is fetched and stored in the local cache.
- Higher zoom levels are the most expensive.
- First-time exploration is usually the worst-case path.

As the world generates, downloaded terrain, surface-water, and ecoregion files are written into a local cache. You can delete this cache at any time without breaking the world, but future generation will be slower until the files are downloaded again.

Once tiles are cached locally, revisiting the same regions should be smoother.

## Runtime Performance Config

Terrarium Expanded reads runtime tuning from:

- `<gameDir>/config/terrarium-expanded.properties`

If the file is missing, built-in low-memory defaults are used.

Supported keys:

- `terrain.chunk_cache_entries` (default `256`)
- `terrain.chunk_cache_ttl_seconds` (default `120`, `0` disables TTL)
- `tiles.io_threads_per_service` (default `2`)
- `tiles.terrain.cache_entries` (default `64`)
- `tiles.terrain.prefetch_radius` (default `0`)
- `tiles.terrain.cache_ttl_seconds` (default `120`, `0` disables TTL)
- `tiles.recovery.cache_entries` (default `64`)
- `tiles.recovery.prefetch_radius` (default `0`)
- `tiles.recovery.cache_ttl_seconds` (default `120`, `0` disables TTL)
- `tiles.surface_water.cache_entries` (default `64`)
- `tiles.surface_water.prefetch_radius` (default `0`)
- `tiles.surface_water.cache_ttl_seconds` (default `120`, `0` disables TTL)
- `tiles.ecoregion.cache_entries` (default `4`)
- `tiles.ecoregion.prefetch_radius` (default `0`)
- `tiles.ecoregion.cache_ttl_seconds` (default `120`, `0` disables TTL)
- `sampling.chunk_local_cache_entries` (default `16`)
- `sampling.biome_local_cache_entries` (default `4`)
- `sampling.thread_local_idle_seconds` (default `10`, `0` disables idle clear)
- `inland_water.enabled` (default `true`)
- `inland_water.min_water_months` (default `10`, clamped to `1-12`)

Memory-oriented baseline profile (good starting point for modpacks):

```properties
terrain.chunk_cache_entries=192
terrain.chunk_cache_ttl_seconds=90
tiles.terrain.cache_entries=48
tiles.terrain.cache_ttl_seconds=60
tiles.recovery.cache_entries=48
tiles.recovery.cache_ttl_seconds=60
tiles.surface_water.cache_entries=48
tiles.surface_water.cache_ttl_seconds=60
tiles.ecoregion.cache_entries=4
tiles.ecoregion.cache_ttl_seconds=90
sampling.chunk_local_cache_entries=8
sampling.biome_local_cache_entries=4
sampling.thread_local_idle_seconds=5
```

## Data Sources

Terrarium Expanded currently uses:

- Terrarium elevation tiles for terrain height
- Global surface-water seasonality tiles for inland water detection
- Reduced WWF ecoregion tiles for land biome selection
- WOA23 mean annual sea-surface temperature as a bundled runtime data layer for ocean biome tiering

Default remote endpoints are currently configured as:

- Terrain: `https://elevation-tiles-prod.s3.amazonaws.com/terrarium`
- Ecoregions: `https://d127t6piqu53ls.cloudfront.net/tiles-reduced`
- Surface water: `https://storage.googleapis.com/global-surface-water/tiles2021/seasonality`

## Current Caveats

- AWS Terrarium source data can contain spikes or odd terrain artifacts.
- Those artifacts can become more noticeable at higher zoom levels.
- Remote-data availability directly affects first-time world generation quality and responsiveness.

For implementation details, see [documentation/WORLDGEN.md](documentation/WORLDGEN.md).

## Development / Local Build

Build from the repo root with:

```powershell
.\gradlew build
```

The project targets Architectury with both Fabric and NeoForge enabled.

## Full Documentation Gallery

### Vanilla (`z=10`)

#### San Francisco

`37.786374, -122.462042`

![San Francisco at z=10](documentation/vanilla%20%28z=10%29/1.%20San%20Francisco.png)

#### Lake Como

`45.794153, 9.053021`

![Lake Como at z=10](documentation/vanilla%20%28z=10%29/2.%20Lake%20Como.png)

#### Mt. Fuji

`35.114543, 138.680079`

![Mt. Fuji at z=10](documentation/vanilla%20%28z=10%29/3.%20Mt.%20Fuji.png)

#### Brisbane

`-27.343835, 153.159104`

![Brisbane at z=10](documentation/vanilla%20%28z=10%29/4.%20Brisbane.png)

#### Fernando Po

`3.486905, 8.559661`

![Fernando Po at z=10](documentation/vanilla%20%28z=10%29/5.%20Fernando%20Po.png)

#### Aden

`12.745366, 45.011938`

![Aden at z=10](documentation/vanilla%20%28z=10%29/6.%20Aden.png)

#### Strait of Gibraltar

`35.979335, -5.368864`

![Strait of Gibraltar at z=10](documentation/vanilla%20%28z=10%29/7.%20Straight%20of%20Gibraltar.png)

#### Montreal

`45.421641, -73.558653`

![Montreal at z=10](documentation/vanilla%20%28z=10%29/8.%20Montreal.png)

#### Grand Canyon

`36.096309, -112.121206`

![Grand Canyon at z=10](documentation/vanilla%20%28z=10%29/9.%20Grand%20Canyon.png)

### Biomes O' Plenty (`z=10`)

#### San Francisco

`37.786374, -122.462042`

![San Francisco at z=10](documentation/biomes-o-plenty%20%28z=10%29/1.%20San%20Francisco.png)

#### Lake Como

`45.794153, 9.053021`

![Lake Como at z=10](documentation/biomes-o-plenty%20%28z=10%29/2.%20Lake%20Como.png)

#### Mt. Fuji

`35.114543, 138.680079`

![Mt. Fuji at z=10](documentation/biomes-o-plenty%20%28z=10%29/3.%20Mt.%20Fuji.png)

#### Brisbane

`-27.343835, 153.159104`

![Brisbane at z=10](documentation/biomes-o-plenty%20%28z=10%29/4.%20Brisbane.png)

#### Fernando Po

`3.486905, 8.559661`

![Fernando Po at z=10](documentation/biomes-o-plenty%20%28z=10%29/5.%20Fernando%20Po.png)

#### Aden

`12.745366, 45.011938`

![Aden at z=10](documentation/biomes-o-plenty%20%28z=10%29/6.%20Aden.png)

#### Strait of Gibraltar

`35.979335, -5.368864`

![Strait of Gibraltar at z=10](documentation/biomes-o-plenty%20%28z=10%29/7.%20Straight%20of%20Gibraltar.png)

#### Montreal

`45.421641, -73.558653`

![Montreal at z=10](documentation/biomes-o-plenty%20%28z=10%29/8.%20Montreal.png)

#### Grand Canyon

`36.096309, -112.121206`

![Grand Canyon at z=10](documentation/biomes-o-plenty%20%28z=10%29/9.%20Grand%20Canyon.png)

### Biomes O' Plenty (`z=12`)

#### Grand Canyon

`36.096309, -112.121206`

![Grand Canyon at z=12](documentation/biomes-o-plenty%20%28z=12%29/Grand%20Canyon.png)
