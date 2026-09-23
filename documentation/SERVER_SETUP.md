# Terrarium Expanded server setup

Earth servers on Fabric and NeoForge use **`level-type=terrarium_expanded:earth`**.
The mod provides the default settings. Installing and editing the starter datapack replaces those
defaults with your settings when creating a new world. The level type stays the same.

## Requirements

- A supported Minecraft server using Fabric or NeoForge, with the Java version required by that
  Minecraft and loader release.
- Terrarium Expanded for the selected loader and Minecraft version.
- Architectury API for the same loader. Fabric also requires Fabric API. Use the loader and
  dependency versions required by your Terrarium Expanded release.
- Install the corresponding mods and their dependencies on connecting clients as required by those mods.
- Optional biome mods, such as Nature's Spirit, must support your loader and Minecraft version;
  install their dependencies too. They do not change the Earth `level-type`.
- Outbound internet access for elevation, ecoregion, and surface-water tiles. The default services
  use `elevation-tiles-prod.s3.amazonaws.com`, `d127t6piqu53ls.cloudfront.net`, and
  `storage.googleapis.com`. Initial generation and uncached exploration can be slow.

Use the mod and starter ZIP from the **same release**, matching your Minecraft version. The
starter's `pack.mcmeta` declares its data pack format compatibility. It requires Terrarium Expanded
and is not a standalone vanilla generator.

## Create an Earth world

1. Install the loader and required mods in the server's `mods` folder. Complete the normal server
   setup, including accepting Minecraft's EULA if you agree to it.
2. Stop the server and choose an unused world name. This guide uses `earth`, which becomes the
   world save folder. If a previous startup created `world`, keep it and use `earth` for the new
   world. The new folder must not contain an existing `level.dat`.
3. In `server.properties`, set:

   ```properties
   level-name=earth
   level-type=terrarium_expanded:earth
   ```

4. To customize generation, [install and edit the starter datapack](#customize-with-the-starter-datapack)
   **before this world's first startup**. Skip this step to use the mod's defaults.
5. Start the server normally, without safe mode, then [verify the settings](#verify-generation).
   A hosting panel must preserve your `server.properties` and datapack files.

Leave `generator-settings` unchanged; Earth options come from the preset JSON in the datapack.
Changing the preset or `level-type` does not reconfigure an existing world.

## Customize with the starter datapack

Keep `level-type=terrarium_expanded:earth`. The starter replaces the mod's Earth preset with an
editable copy containing all settings.

1. Download `terrarium-expanded-server-earth-<mod-version>-mc<minecraft-version>.zip` from the
   [matching GitHub release](https://github.com/intplex/terrarium-expanded/releases).
   Developers can also copy the repository's `datapacks/server-earth` folder, or run
   `./gradlew serverEarthDatapack` and use the ZIP in `build/distributions`.
2. With the server stopped, manually create `<server>/earth/datapacks/server-earth/`.
   `earth` must match `level-name`; `server-earth` is the datapack folder. Creating these folders
   does not generate a world.
3. Extract the ZIP contents into that `server-earth` folder. The installed layout must be:

   ```text
   <server>/
   ├── server.properties
   ├── mods/
   └── earth/
       └── datapacks/
           └── server-earth/
               ├── pack.mcmeta
               ├── README.md
               ├── schema/
               │   └── earth-world-preset.schema.json
               └── data/
                   └── terrarium_expanded/
                       └── worldgen/
                           └── world_preset/
                               └── earth.json
   ```

   A common error is `server-earth/server-earth/pack.mcmeta`. Remove the extra wrapper folder.
   Minecraft also accepts the ZIP directly in `datapacks`, but an extracted folder is easier to edit.
   Install only one copy, not both the ZIP and extracted folder.
4. Open `data/terrarium_expanded/worldgen/world_preset/earth.json` inside the pack.
   Edit values under `dimensions` → `minecraft:overworld` → `generator` →
   `biome_source`. Leave the generator type, noise-settings ID, and other dimensions intact.
5. Save the file and start the server. Minecraft loads the enabled pack's settings when creating
   the new Earth world. Check that the [startup summary](#verify-generation) matches your edits.

If the pack is missing or disabled, a new Earth world uses the mod's defaults. Check the settings
before letting players explore.

The pack is server-side data. Players do not need a separate copy of this datapack; they still
need any client-required mods. Keep the datapack with the world when moving or backing it up.

## Editing and the offline schema

Every supported Earth setting is included in the starter. Its `$schema` line links to a schema
inside the pack. Open the **extracted** JSON in a schema-aware editor such as VS Code for field
suggestions, descriptions, enum choices, and range checks. No schema download is required.

A plain text editor or hosting-panel file editor also works. JSON requires quoted property names,
lowercase `true`/`false`, commas between entries, and no trailing commas or comments. The examples
below show values to change **inside the existing biome_source object**, not replacement files.

The schema is editor assistance. Minecraft uses the mod's codecs at startup, not the schema file.
Unknown-key highlighting is therefore useful: a misspelled setting may otherwise be ignored.
Height relationships and loaded dimension limits also need runtime checks.

The pack's `earth.json` replaces the whole bundled preset; individual fields are not merged.
Keep the complete file, including Nether and End. If another datapack also replaces this Earth
preset, Minecraft uses the copy from the pack with the highest priority.

## Earth options

These fields are inside `biome_source`. Defaults below are the starter's explicit values.

| Field | Default | Meaning and accepted values |
| --- | --- | --- |
| `type` | `terrarium_expanded:ecoregion_tiles` | Required biome source; leave unchanged. |
| `zoom` | `8` | Integer `8–12`; determines map size and real-world distance per block. |
| `max_mountain_y` | `256` | Mountain height limit; integer `2–2031`, above sea level and within the world's build height. Values above `319` require a compatible mod or datapack that raises the build height. |
| `ocean_floor_y` | `0` | Mapped ocean floor; integer `0–2029`, strictly below sea level. |
| `sea_level` | `63` | Integer `1–2030`; must satisfy `ocean_floor_y < sea_level < max_mountain_y`. |
| `below_sea_height_mode` | `even_scale` | Ocean-depth mapping; modes explained below. |
| `above_sea_height_mode` | `even_scale` | Land-elevation mapping; modes explained below. |
| `terrain_base_url` | `https://elevation-tiles-prod.s3.amazonaws.com/terrarium` | Compatible elevation PNG tile service. Normally leave unchanged. |
| `biomes_base_url` | `https://d127t6piqu53ls.cloudfront.net/tiles-reduced` | Compatible reduced ecoregion PNG tile service. |
| `surface_water_base_url` | `https://storage.googleapis.com/global-surface-water/tiles2021/seasonality` | Compatible surface-water seasonality PNG tile service. |
| `terrain_fixes` | `none` | Reserved selection; `none` is the only implemented choice. |
| `world_border` | `false` | Apply a square Earth map border around `(0,0)`, inset 32 chunks from map edges. |
| `spawn_latitude` | `0.442221` | Degrees, north positive; `-85.0511287798066` to `85.0511287798066`. |
| `spawn_longitude` | `33.150150` | Degrees, east positive; `-180` to `180`. |
| `biome_integration` | `auto` | `auto`, `vanilla`, `biomes_o_plenty`, `regions_unexplored`, or `natures_spirit`. Legacy `expanded` means `biomes_o_plenty`. |
| `generation.caves` | `false` | Enable caves. Fluids are controlled separately. |
| `generation.canyons` | `false` | Enable canyon/ravine carvers. |
| `generation.extra_underground` | `false` | Enable extra underground cave carvers. |
| `generation.aquifers` | `false` | Enable underground fluid distribution. Does not enable cave carving itself. |
| `generation.lava_aquifers` | `false` | Allow aquifer lava when `aquifers=true`; otherwise ignored. |
| `generation.villages` | `false` | Allow villages; also requires `generate-structures=true`. Other structures are not controlled by this field. |
| `inland_water.enabled` | `true` | Generate inland lakes/rivers from water tiles. |
| `inland_water.min_water_months` | `10` | Integer `1–12`; lower values include more seasonal water. |

The dotted names above are nested JSON objects, not literal dotted keys. `auto` chooses among
loaded biome providers using the mod's per-biome priorities. Selecting a provider does not install
that mod; use a compatible version and its dependencies.

Height modes: `even_scale` is linear; `sea_level_detail` expands detail near sea level;
`high_elevation_detail` expands detail near the highest elevations/deepest depths;
`compressed_middle_heights` compresses the middle of the height range. Choose above and below
sea level independently.

Minecraft normally allows blocks up to **Y=319**. For taller mountains, install an additional mod
or datapack that raises the build height and supports Terrarium Expanded, before creating the
world. Setting `max_mountain_y` to `512`, for example, cannot raise the build height on its own.

| Zoom | Map width and length in blocks | Approximate equatorial scale |
| --- | --- | --- |
| `8` | `65,536` | `611.50 m/block` |
| `9` | `131,072` | `305.75 m/block` |
| `10` | `262,144` | `152.87 m/block` |
| `11` | `524,288` | `76.44 m/block` |
| `12` | `1,048,576` | `38.22 m/block` |

Higher zoom increases map size, remote tile demand, and generation cost. Mercator scale varies
with latitude; the table is an equatorial approximation.

## Examples

For zoom 10, Nature's Spirit, and a spawn near San Francisco, edit these existing fields:

```json
"zoom": 10,
"biome_integration": "natures_spirit",
"spawn_latitude": 37.786374,
"spawn_longitude": -122.462042
```

For caves with water aquifers, no lava aquifers, and villages:

```json
"generation": {
  "caves": true,
  "canyons": false,
  "extra_underground": false,
  "aquifers": true,
  "lava_aquifers": false,
  "villages": true
}
```

Also set `generate-structures=true` in `server.properties` for villages. To include more seasonal
inland water, change its threshold before creating the world:

```json
"inland_water": {
  "enabled": true,
  "min_water_months": 6
}
```

## Verify generation

- Run `/datapack list enabled` as an operator, or `datapack list enabled` in the server console.
  If you installed the starter, it should list `file/server-earth` (or the ZIP filename).
- Find `[TX-WORLDGEN] Active Earth generator` in the server log. It reports the actual active
  zoom, terrain heights, biome integration, spawn, border, inland-water settings, and generation toggles.
  Compare these values with your edits: for example, setting `zoom` to `10` should show `zoom=10`.
- An operator can explore a known location with `/tplatlong 37.786374 -122.462042`.
  Tile downloads may delay uncached generation. The Earth layout comes from geographic data;
  changing the Minecraft seed does not select a different continent layout.

Spawn coordinates and an enabled map border are reapplied from the saved profile at startup.
Account for this if also using `/setworldspawn` or `/worldborder` commands.

## Existing worlds and runtime tuning

The chosen generation settings are serialized into the world's `level.dat`. Changing the preset,
`level-type`, or running `/reload` does not reconfigure its saved generator. Do not edit `level.dat`
as a routine setup step. To test new settings, stop the server, choose a new `level-name`, create
that folder's datapack directory, copy the edited pack there, and restart. Keep the original world.

Performance settings live in `<server>/config/terrarium-expanded.properties`. The mod creates a
commented default file when missing. Changes apply after a restart and include cache memory budgets,
cache expiry, download worker counts, and prefetch radii. See the root README's runtime configuration
reference. The worldgen JSON does not control JVM heap size or server view/simulation distance.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Normal-looking vanilla world | Confirm the correct `level-type`, a genuinely new world folder, and the `[TX-WORLDGEN]` summary. Existing worlds keep their old generator. |
| `Failed to parse level-type ... defaulting to minecraft:normal` | The preset ID is invalid or unavailable. Check spelling, pack placement, loader/mod installation, and enabled packs. Stop and retry with a new world after fixing it. |
| Custom pack missing from `/datapack list enabled` | `pack.mcmeta` must be at the pack root. Check ZIP nesting, exact world path, safe mode, and disabled packs. For a new world, inspect `initial-disabled-packs` and any host-managed pack settings; preserve loader-required enabled packs. |
| `Failed to load datapacks` or codec/registry errors | Check earlier log lines for the resource and field. Validate JSON and numeric ranges, confirm required mods are loaded, and use the matching pack/mod/Minecraft versions. Do not choose safe mode to bypass a broken Earth preset. |
| Incompatible pack version | Use the starter from the matching mod release and Minecraft version. Changing format numbers in `pack.mcmeta` alone does not make a pack compatible with another game version. |
| Biomes are missing or unexpected | Verify the biome mod and its dependencies match the loader/version; inspect `biome_integration` and the startup logs. |
| Starter is enabled but my settings are not used | Check for an existing `level.dat` or another pack replacing the Earth preset. Existing worlds keep their saved settings; among packs replacing the same preset, the highest-priority pack wins. |
| Earth terrain disappears after adding another world-generation pack | That pack may select its own terrain generator, replacing Earth generation. Test a new world without it, or use a version compatible with Terrarium Expanded. Confirm the log reports `Active Earth generator`. |
| Stalls, incomplete terrain, or water/biome problems during exploration | Check remote tile connectivity and download errors. Higher zoom and uncached generation cost more. Preserve logs when reporting failures. |
| Properties or preset edits have no effect | Performance properties need a restart. Saved generation settings need a new world. |

When reporting a problem, include Minecraft/mod/loader versions, your `level-type`, the datapack
folder layout, whether the world already existed, and the relevant startup errors and `[TX-WORLDGEN]`
line. Remove credentials or private service URLs from logs before sharing them.
