package com.github.intplex.earth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EarthWorldPresetTest {
    @Test
    void earthPresetUsesEcoregionBiomeSource() throws Exception {
        try (InputStream stream = EarthWorldPresetTest.class.getResourceAsStream(
            "/data/terrarium_expanded/worldgen/world_preset/earth.json"
        )) {
            assertNotNull(stream, "earth world preset should exist");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonObject biomeSource = root
                .getAsJsonObject("dimensions")
                .getAsJsonObject("minecraft:overworld")
                .getAsJsonObject("generator")
                .getAsJsonObject("biome_source");

            assertEquals("terrarium_expanded:ecoregion_tiles", biomeSource.get("type").getAsString());
            assertEquals(8, biomeSource.get("zoom").getAsInt());
            assertEquals(256, biomeSource.get("max_mountain_y").getAsInt());
            assertEquals(0, biomeSource.get("ocean_floor_y").getAsInt());
            assertEquals(63, biomeSource.get("sea_level").getAsInt());
            assertEquals("even_scale", biomeSource.get("below_sea_height_mode").getAsString());
            assertEquals("even_scale", biomeSource.get("above_sea_height_mode").getAsString());
            assertEquals("https://elevation-tiles-prod.s3.amazonaws.com/terrarium", biomeSource.get("terrain_base_url").getAsString());
            assertEquals("https://d127t6piqu53ls.cloudfront.net/tiles-reduced", biomeSource.get("biomes_base_url").getAsString());
            assertEquals("https://storage.googleapis.com/global-surface-water/tiles2021/seasonality", biomeSource.get("surface_water_base_url").getAsString());
            assertEquals("none", biomeSource.get("terrain_fixes").getAsString());
            assertFalse(biomeSource.get("world_border").getAsBoolean());
            assertEquals(0.442221, biomeSource.get("spawn_latitude").getAsDouble());
            assertEquals(33.150150, biomeSource.get("spawn_longitude").getAsDouble());
            assertEquals("auto", biomeSource.get("biome_integration").getAsString());
            JsonObject generation = biomeSource.getAsJsonObject("generation");
            assertFalse(generation.get("caves").getAsBoolean());
            assertFalse(generation.get("canyons").getAsBoolean());
            assertFalse(generation.get("extra_underground").getAsBoolean());
            assertFalse(generation.get("aquifers").getAsBoolean());
            assertFalse(generation.get("lava_aquifers").getAsBoolean());
            assertFalse(generation.get("villages").getAsBoolean());
        }
    }
}
