package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceMathTest {
    @Test
    void depthFromTerrainYUsesExpectedAnchors() {
        assertEquals(0.0, TerrainService.depthFromTerrainY(EarthGenConfig.SEA_LEVEL), 1.0e-12);
        assertEquals(-1.0, TerrainService.depthFromTerrainY(EarthGenConfig.MIN_TERRAIN_Y), 1.0e-12);
        assertEquals(1.0, TerrainService.depthFromTerrainY(EarthGenConfig.MAX_TERRAIN_Y), 1.0e-12);
    }

    @Test
    void depthFromTerrainYIsMonotonicAcrossTerrainRange() {
        double last = TerrainService.depthFromTerrainY(EarthGenConfig.MIN_TERRAIN_Y);
        for (int y = EarthGenConfig.MIN_TERRAIN_Y + 1; y <= EarthGenConfig.MAX_TERRAIN_Y; y++) {
            double current = TerrainService.depthFromTerrainY(y);
            assertTrue(current >= last, "Depth should be non-decreasing as terrainY increases");
            last = current;
        }
    }

    @Test
    void continentalnessFromReliefTracksRelativeHeight() {
        double low = TerrainMetricsKernel.continentalnessFromRelief(80, 80, 120);
        double mid = TerrainMetricsKernel.continentalnessFromRelief(100, 80, 120);
        double high = TerrainMetricsKernel.continentalnessFromRelief(120, 80, 120);

        assertTrue(low < 0.0);
        assertEquals(0.0, mid, 1.0e-12);
        assertTrue(high > 0.0);
    }

    @Test
    void erosionFromSteepnessIsClampedToRange() {
        assertEquals(-1.0, TerrainMetricsKernel.erosionFromSteepness(0.0), 1.0e-12);
        assertEquals(1.0, TerrainMetricsKernel.erosionFromSteepness(10.0), 1.0e-12);
    }

    @Test
    void chunkCacheCapacityReadsSystemPropertySafely() {
        String key = TerrainService.CHUNK_CACHE_ENTRIES_PROPERTY;
        String original = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(512, TerrainService.chunkCacheEntryCapacity());

            System.setProperty(key, "1024");
            assertEquals(1024, TerrainService.chunkCacheEntryCapacity());

            System.setProperty(key, "-1");
            assertEquals(512, TerrainService.chunkCacheEntryCapacity());

            System.setProperty(key, "not-a-number");
            assertEquals(512, TerrainService.chunkCacheEntryCapacity());
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    @Test
    void suppressIsolatedPositiveSpikeFlattensIsolatedOutlier() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            100,
            103,
            8,
            0
        );

        assertEquals(103, filtered);
    }

    @Test
    void suppressIsolatedPositiveSpikeKeepsSupportedMountainPeak() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            116,
            126,
            8,
            2
        );

        assertEquals(130, filtered);
    }

    @Test
    void suppressIsolatedPositiveSpikeKeepsTerrainWhenLocalReliefIsAlreadyHigh() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            108,
            126,
            8,
            0
        );

        assertEquals(130, filtered);
    }
}
