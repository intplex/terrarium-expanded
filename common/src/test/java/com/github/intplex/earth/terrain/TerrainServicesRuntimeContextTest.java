package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainServicesRuntimeContextTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void profileSwitchRebuildsRuntimeContext() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();

        TerrainServices.syncEarthSettings(
            EarthGenConfig.DEFAULT_ZOOM + 1,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
        );

        EarthRuntimeContext switched = TerrainServices.requireContext();
        assertNotSame(initial, switched);
    }

    @Test
    void zoomSwitchRebuildsOnlyZoomSensitiveServices() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();

        TerrainServices.syncEarthSettings(
            EarthGenConfig.DEFAULT_ZOOM + 1,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
        );

        EarthRuntimeContext switched = TerrainServices.requireContext();
        assertNotSame(initial, switched);
        assertNotSame(initial.services().tileService(), switched.services().tileService());
        assertNotSame(initial.services().surfaceWaterTileService(), switched.services().surfaceWaterTileService());
        assertSame(initial.services().recoveryTileService(), switched.services().recoveryTileService());
        assertSame(initial.services().ecoregionTileService(), switched.services().ecoregionTileService());
    }

    @Test
    void profileOnlySwitchReusesServiceInstances() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();

        TerrainServices.syncEarthSettings(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y - 5,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y + 5
        );

        EarthRuntimeContext switched = TerrainServices.requireContext();
        assertNotSame(initial, switched);
        assertNotSame(initial.terrainRuntimeState(), switched.terrainRuntimeState());
        assertSame(initial.services().tileService(), switched.services().tileService());
        assertSame(initial.services().recoveryTileService(), switched.services().recoveryTileService());
        assertSame(initial.services().ecoregionTileService(), switched.services().ecoregionTileService());
        assertSame(initial.services().surfaceWaterTileService(), switched.services().surfaceWaterTileService());
    }

    @Test
    void staticAdaptersDelegateToContextServices() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext context = TerrainServices.requireContext();
        assertNotNull(context.services());
        assertSame(context.services().tileService(), TerrainServices.tileService());
        assertSame(context.services().recoveryTileService(), TerrainServices.recoveryTileService());
        assertSame(context.services().ecoregionTileService(), TerrainServices.ecoregionTileService());
        assertSame(context.services().surfaceWaterTileService(), TerrainServices.surfaceWaterTileService());
    }

    @Test
    void clearingCachesKeepsSameRuntimeContextInstance() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();

        TerrainServices.clearRuntimeCaches();

        EarthRuntimeContext afterClear = TerrainServices.requireContext();
        assertSame(initial, afterClear);
    }

    @Test
    void refreshRuntimeContextForTestingRebuildsWithRequestedProfile() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();

        EarthRuntimeContext refreshed = TerrainServices.refreshRuntimeContextForTesting(
            new EarthGenerationProfile(
                EarthGenConfig.DEFAULT_ZOOM,
                EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y - 4,
                EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y + 4
            )
        );

        assertNotSame(initial, refreshed);
        assertSame(refreshed, TerrainServices.requireContext());
    }

    @Test
    void syncEarthSettingsPreservesNonShapePresetFields() {
        TerrainServices.bootstrap(tempDir);
        EarthGenerationProfile customProfile = new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
            "https://example.com/terrarium",
            "https://example.com/biomes",
            "https://example.com/water",
            "none",
            new EarthWorldgenToggles(
                false,
                false,
                false,
                false,
                false,
                false
            ),
            false,
            10.5,
            20.5
        );
        TerrainServices.syncEarthProfile(customProfile);

        TerrainServices.syncEarthSettings(
            EarthGenConfig.DEFAULT_ZOOM + 1,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
        );

        EarthGenerationProfile afterZoomChange = TerrainServices.requireContext().profile();
        assertEquals("https://example.com/terrarium", afterZoomChange.terrainBaseUrl());
        assertEquals("https://example.com/biomes", afterZoomChange.biomesBaseUrl());
        assertEquals("https://example.com/water", afterZoomChange.surfaceWaterBaseUrl());
        assertEquals("none", afterZoomChange.terrainFixes());
        assertEquals(false, afterZoomChange.worldgenToggles().villages());
        assertEquals(false, afterZoomChange.worldBorder());
        assertEquals(10.5, afterZoomChange.spawnLatitude());
        assertEquals(20.5, afterZoomChange.spawnLongitude());
    }
}
