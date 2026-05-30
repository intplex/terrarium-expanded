package com.github.intplex.earth.terrain;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainHeightModeTest {
    @Test
    void codecParsesSerializedNames() {
        assertEquals(
            TerrainHeightMode.SEA_LEVEL_DETAIL,
            TerrainHeightMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("sea_level_detail")).result().orElseThrow()
        );
        assertEquals(
            TerrainHeightMode.HIGH_ELEVATION_DETAIL,
            TerrainHeightMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("high_elevation_detail")).result().orElseThrow()
        );
        assertEquals(
            TerrainHeightMode.COMPRESSED_MIDDLE_HEIGHTS,
            TerrainHeightMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("compressed_middle_heights")).result().orElseThrow()
        );
    }

    @Test
    void codecRejectsInvalidSerializedNames() {
        assertTrue(TerrainHeightMode.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("invalid")).error().isPresent());
    }
}
