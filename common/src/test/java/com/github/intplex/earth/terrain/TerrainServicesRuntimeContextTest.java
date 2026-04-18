package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void recoveryTileServiceIsLazyByZoomThreshold() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext initial = TerrainServices.requireContext();
        assertNull(initial.services().recoveryTileService());

        TerrainServices.syncEarthSettings(11, EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y, EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y);
        EarthRuntimeContext upgraded = TerrainServices.requireContext();
        assertNotNull(upgraded.services().recoveryTileService());
    }

    @Test
    void zoomSwitchRebuildsRuntimeServices() {
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
        assertNotSame(initial.services().ecoregionTileService(), switched.services().ecoregionTileService());
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
        assertSame(initial.services().ecoregionTileService(), switched.services().ecoregionTileService());
        assertSame(initial.services().surfaceWaterTileService(), switched.services().surfaceWaterTileService());
    }

    @Test
    void staticAdaptersDelegateToContextServices() {
        TerrainServices.bootstrap(tempDir);
        EarthRuntimeContext context = TerrainServices.requireContext();
        assertNotNull(context.services());
        assertSame(context.services().tileService(), TerrainServices.tileService());
        if (context.services().recoveryTileService() != null) {
            assertSame(context.services().recoveryTileService(), TerrainServices.recoveryTileService());
        } else {
            assertNull(context.services().recoveryTileService());
        }
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

    @Test
    void bootstrapAppliesRuntimeConfigToServicesAndTerrainState() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(
            configDir.resolve(TerrariumRuntimeConfig.FILE_NAME),
            "memory.total_budget_mb=160\n"
                + "memory.tiles_budget_percent=80\n"
                + "memory.tile_ttl_seconds=31\n"
                + "memory.snapshot_ttl_seconds=90\n"
                + "memory.local_chunk_entries=18\n"
                + "memory.local_biome_entries=5\n"
                + "memory.local_idle_seconds=7\n"
                + "io.shared_tile_threads=5\n"
                + "tiles.terrain.prefetch_radius=1\n"
                + "tiles.recovery.prefetch_radius=2\n"
                + "tiles.surface_water.prefetch_radius=3\n"
                + "tiles.ecoregion.prefetch_radius=0\n"
                + "inland_water.enabled=false\n"
                + "inland_water.min_water_months=4\n"
        );

        TerrainServices.bootstrap(tempDir);
        TerrainServices.syncEarthSettings(11, EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y, EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y);
        EarthRuntimeContext context = TerrainServices.requireContext();
        TerrariumRuntimeConfig config = TerrainServices.runtimeConfig();
        long totalTileBytes = config.tileBudgetBytes();
        long unit = totalTileBytes / 9L;
        long terrainBudget = unit * 3L + (totalTileBytes - unit * 9L);
        long recoveryBudget = unit * 2L;
        long surfaceBudget = unit * 3L;
        long ecoregionBudget = unit;

        assertEquals(config.snapshotBudgetBytes(), context.terrainRuntimeState().snapshotCacheMaxWeightBytes());
        assertEquals(90, context.terrainRuntimeState().snapshotCacheTtlSeconds());
        assertEquals(18, context.terrainRuntimeState().chunkLocalCacheEntries());
        assertEquals(5, context.terrainRuntimeState().biomeLocalCacheEntries());
        assertEquals(7, context.terrainRuntimeState().threadLocalIdleSeconds());
        assertEquals(false, context.terrainRuntimeState().inlandWaterSettings().enabled());
        assertEquals(4, context.terrainRuntimeState().inlandWaterSettings().minWaterMonths());

        assertEquals(terrainBudget, context.services().tileService().configuredMemoryCacheMaxWeightBytes());
        assertEquals(31, context.services().tileService().configuredMemoryCacheTtlSeconds());
        assertEquals(1, context.services().tileService().configuredPrefetchRadius());
        assertEquals(5, context.services().tileService().configuredIoThreads());

        assertNotNull(context.services().recoveryTileService());
        assertEquals(recoveryBudget, context.services().recoveryTileService().configuredMemoryCacheMaxWeightBytes());
        assertEquals(31, context.services().recoveryTileService().configuredMemoryCacheTtlSeconds());
        assertEquals(2, context.services().recoveryTileService().configuredPrefetchRadius());
        assertEquals(5, context.services().recoveryTileService().configuredIoThreads());

        assertEquals(surfaceBudget, context.services().surfaceWaterTileService().configuredMemoryCacheMaxWeightBytes());
        assertEquals(31, context.services().surfaceWaterTileService().configuredMemoryCacheTtlSeconds());
        assertEquals(3, context.services().surfaceWaterTileService().configuredPrefetchRadius());
        assertEquals(5, context.services().surfaceWaterTileService().configuredIoThreads());

        assertEquals(ecoregionBudget, context.services().ecoregionTileService().configuredMemoryCacheMaxWeightBytes());
        assertEquals(31, context.services().ecoregionTileService().configuredMemoryCacheTtlSeconds());
        assertEquals(0, context.services().ecoregionTileService().configuredPrefetchRadius());
        assertEquals(5, context.services().ecoregionTileService().configuredIoThreads());
    }
}
