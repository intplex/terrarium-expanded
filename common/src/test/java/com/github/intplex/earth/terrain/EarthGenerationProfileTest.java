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
        assertEquals(TerrainHeightMode.EVEN_SCALE, profile.belowSeaHeightMode());
        assertEquals(TerrainHeightMode.EVEN_SCALE, profile.aboveSeaHeightMode());
    }

    @Test
    void storesExplicitHeightModes() {
        EarthGenerationProfile profile = new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
            EarthGenConfig.DEFAULT_SEA_LEVEL,
            TerrainHeightMode.SEA_LEVEL_DETAIL,
            TerrainHeightMode.COMPRESSED_MIDDLE_HEIGHTS,
            EarthGenerationProfile.DEFAULT_TERRAIN_BASE_URL,
            EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL,
            EarthGenerationProfile.DEFAULT_SURFACE_WATER_BASE_URL,
            EarthGenerationProfile.TERRAIN_FIXES_NONE,
            EarthWorldgenToggles.defaults(),
            false,
            EarthGenerationProfile.DEFAULT_SPAWN_LATITUDE,
            EarthGenerationProfile.DEFAULT_SPAWN_LONGITUDE
        );

        assertEquals(TerrainHeightMode.SEA_LEVEL_DETAIL, profile.belowSeaHeightMode());
        assertEquals(TerrainHeightMode.COMPRESSED_MIDDLE_HEIGHTS, profile.aboveSeaHeightMode());
    }

    @Test
    void normalizesNullHeightModesToEvenScale() {
        EarthGenerationProfile profile = new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
            EarthGenConfig.DEFAULT_SEA_LEVEL,
            null,
            null,
            EarthGenerationProfile.DEFAULT_TERRAIN_BASE_URL,
            EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL,
            EarthGenerationProfile.DEFAULT_SURFACE_WATER_BASE_URL,
            EarthGenerationProfile.TERRAIN_FIXES_NONE,
            EarthWorldgenToggles.defaults(),
            false,
            EarthGenerationProfile.DEFAULT_SPAWN_LATITUDE,
            EarthGenerationProfile.DEFAULT_SPAWN_LONGITUDE
        );

        assertEquals(TerrainHeightMode.EVEN_SCALE, profile.belowSeaHeightMode());
        assertEquals(TerrainHeightMode.EVEN_SCALE, profile.aboveSeaHeightMode());
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
