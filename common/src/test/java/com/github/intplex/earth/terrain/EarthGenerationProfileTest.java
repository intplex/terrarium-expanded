package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EarthGenerationProfileTest {
    @Test
    void legacyConstructorDefaultsSeaLevel() {
        EarthGenerationProfile profile = new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
        );

        assertEquals(EarthGenConfig.DEFAULT_SEA_LEVEL, profile.seaLevel());
    }

    @Test
    void rejectsSeaLevelOutsideTerrainShape() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EarthGenerationProfile(8, 100, 20, 20)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new EarthGenerationProfile(8, 100, 20, 100)
        );
    }
}
