package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceWaterCoverageSettingsTest {
    @Test
    void defaultsAreFixedInCode() {
        assertEquals(-60.0, SurfaceWaterCoverageSettings.DEFAULT.minLatitude());
        assertEquals(77.0, SurfaceWaterCoverageSettings.DEFAULT.maxLatitude());
        assertEquals(SurfaceWaterCoverageSettings.DEFAULT_MIN_LATITUDE, SurfaceWaterCoverageSettings.DEFAULT.minLatitude());
        assertEquals(SurfaceWaterCoverageSettings.DEFAULT_MAX_LATITUDE, SurfaceWaterCoverageSettings.DEFAULT.maxLatitude());
    }

    @Test
    void tileIntersectionChecksCoverageRange() {
        SurfaceWaterCoverageSettings settings = SurfaceWaterCoverageSettings.DEFAULT;
        assertFalse(settings.intersectsTile(new TileKey(128, 194), 8));
        assertTrue(settings.intersectsTile(new TileKey(128, 128), 8));
    }
}
