package com.github.intplex.earth.biome;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndergroundBiomeTagResourceTest {
    @Test
    void undergroundBiomeTagContainsVanillaAndOptionalIntegrationEntries() throws Exception {
        assertCaveTagEntries("/data/terrarium_expanded/tags/worldgen/biome/is_underground.json");
    }

    @Test
    void commonCaveBiomeTagContainsSameLocateEntries() throws Exception {
        assertCaveTagEntries("/data/c/tags/worldgen/biome/is_cave.json");
    }

    private static void assertCaveTagEntries(String resourcePath) throws Exception {
        try (InputStream stream = UndergroundBiomeTagResourceTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "underground biome tag should exist");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray values = root.getAsJsonArray("values");

            Map<String, Boolean> entries = new HashMap<>();
            for (JsonElement value : values) {
                assertTrue(value.isJsonObject(), "underground biome tag entries should use object form consistently");
                JsonObject object = value.getAsJsonObject();
                entries.put(object.get("id").getAsString(), object.get("required").getAsBoolean());
            }

            assertTrue(entries.get("minecraft:lush_caves"));
            assertTrue(entries.get("minecraft:dripstone_caves"));
            assertTrue(entries.get("minecraft:deep_dark"));

            assertFalse(entries.get("biomesoplenty:glowing_grotto"));
            assertFalse(entries.get("biomesoplenty:spider_nest"));
            assertFalse(entries.get("regions_unexplored:ancient_delta"));
            assertFalse(entries.get("regions_unexplored:bioshroom_caves"));
            assertFalse(entries.get("regions_unexplored:prismachasm"));
            assertFalse(entries.get("regions_unexplored:redstone_caves"));
            assertFalse(entries.get("regions_unexplored:scorching_caves"));
        }
    }
}
