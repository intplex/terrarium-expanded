# Server Earth starter datapack

Use this pack with the matching Terrarium Expanded release and Minecraft version, on Fabric or
NeoForge. This pack requires the mod; it cannot generate Earth on a vanilla server.

Use **`level-type=terrarium_expanded:earth`** with or without this pack. When enabled, the pack
replaces the mod's default Earth preset with your edited settings for new worlds.

1. Stop the server. Choose a **new** world name, for example `earth`.
2. Create `<server>/earth/datapacks/server-earth/` and extract the ZIP contents there before starting
   the world. Here `earth` is the world folder selected by `level-name`; `server-earth` is the
   datapack folder.
   `pack.mcmeta` must be directly inside `server-earth`, alongside `data` and `schema`.
3. Edit `data/terrarium_expanded/worldgen/world_preset/earth.json` in a text editor.
   Change values under `dimensions` → `minecraft:overworld` → `generator` → `biome_source`.
   All available settings are already included. Keep the other dimensions intact.
4. Set these lines in the server's `server.properties`:

   ```properties
   level-name=earth
   level-type=terrarium_expanded:earth
   ```

5. Start the server. Check that `/datapack list enabled` lists `file/server-earth`, and that the
   `[TX-WORLDGEN] Active Earth generator` line in the log matches your edited settings.
   If the pack is missing or disabled, a new Earth world uses the mod's defaults.

The preset's `$schema` reference points to the included offline schema. Editors such as VS Code
can suggest fields and explain values when editing the extracted JSON. Plain text editors and
hosting-panel file editors also work: preserve JSON commas, quotes, and braces; do not add comments.

The defaults match the mod's built-in Earth preset: zoom 8, automatic biome integration, and no
caves or villages. For a larger map set `zoom` to `10`; to enable caves set `generation.caves` to
`true`. Inland water defaults to enabled with a ten-month threshold.

Generation settings are saved with the world. Editing this pack, changing `level-type`, or running
`/reload` will not reconfigure an existing world's saved generator. Keep the pack installed with
that world. Use a new world folder to test changed settings and keep your old world as a backup.

The pack replaces the complete Earth preset; individual settings are not merged. Keep the full
file, including Nether and End. Only install one copy of this pack, either the extracted folder
or the ZIP. If another datapack replaces the Earth preset, the highest-priority pack wins.
Performance settings belong in
`config/terrarium-expanded.properties` and apply after a server restart.

See the [server-owner guide](https://github.com/intplex/terrarium-expanded/blob/main/documentation/SERVER_SETUP.md)
for requirements, all options, and troubleshooting. Use the guide from your release tag to match
the version you are running.
