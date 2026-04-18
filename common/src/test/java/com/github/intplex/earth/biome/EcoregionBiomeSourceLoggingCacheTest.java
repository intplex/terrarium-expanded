package com.github.intplex.earth.biome;

import com.github.intplex.earth.terrain.TileKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionBiomeSourceLoggingCacheTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void tearDown() {
        EcoregionBiomeSource.resetFallbackCounters();
    }

    @Test
    void unmappedColorLogDedupeSetIsBoundedAndEvictsOldEntries() {
        int inserts = 9_000;
        for (int i = 0; i < inserts; i++) {
            assertTrue(EcoregionBiomeSource.markUnmappedColorForTesting(new TileKey(i, 0), i));
        }
        assertEquals(8_192, EcoregionBiomeSource.loggedUnmappedColorCountForTesting());

        assertFalse(EcoregionBiomeSource.markUnmappedColorForTesting(new TileKey(inserts - 1, 0), inserts - 1));
        assertTrue(EcoregionBiomeSource.markUnmappedColorForTesting(new TileKey(0, 0), 0));
        assertEquals(8_192, EcoregionBiomeSource.loggedUnmappedColorCountForTesting());
    }
}
