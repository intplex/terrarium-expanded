package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EarthRuntimeServices {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final EarthRuntimeServices EMPTY = new EarthRuntimeServices(null, null, null, null, null, false);
    private static final int TERRAIN_WEIGHT = 3;
    private static final int RECOVERY_WEIGHT = 2;
    private static final int SURFACE_WATER_WEIGHT = 3;
    private static final int ECOREGION_WEIGHT = 1;

    private final TerrariumTileService tileService;
    private final TerrariumTileService recoveryTileService;
    private final EcoregionTileService ecoregionTileService;
    private final SurfaceWaterTileService surfaceWaterTileService;
    private final ExecutorService sharedTileExecutor;
    private final boolean ownsSharedTileExecutor;

    EarthRuntimeServices(
        TerrariumTileService tileService,
        TerrariumTileService recoveryTileService,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService,
        ExecutorService sharedTileExecutor,
        boolean ownsSharedTileExecutor
    ) {
        this.tileService = tileService;
        this.recoveryTileService = recoveryTileService;
        this.ecoregionTileService = ecoregionTileService;
        this.surfaceWaterTileService = surfaceWaterTileService;
        this.sharedTileExecutor = sharedTileExecutor;
        this.ownsSharedTileExecutor = ownsSharedTileExecutor;
    }

    static EarthRuntimeServices empty() {
        return EMPTY;
    }

    static EarthRuntimeServices create(Path gameDir, EarthGenerationProfile profile, TerrariumRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(gameDir, "gameDir");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");

        ExecutorService sharedExecutor = RemotePngTileStore.createDefaultExecutor(runtimeConfig.sharedTileThreads());
        boolean includeRecovery = OceanBathymetryRecovery.isRecoveryActiveForZoom(profile.zoom());
        TileLayerBudgets budgets = allocateTileLayerBudgets(runtimeConfig.tileBudgetBytes(), includeRecovery);
        int tileTtlSeconds = runtimeConfig.tileTtlSeconds();

        TerrariumTileService terrain = TerrariumTileService.create(
            gameDir,
            profile.zoom(),
            profile.terrainBaseUrl(),
            runtimeConfig.terrainTiles(),
            budgets.terrainBytes(),
            tileTtlSeconds,
            sharedExecutor,
            false
        );
        TerrariumTileService recovery = includeRecovery
            ? TerrariumTileService.create(
                gameDir,
                OceanBathymetryRecovery.SOURCE_ZOOM,
                profile.terrainBaseUrl(),
                runtimeConfig.recoveryTiles(),
                budgets.recoveryBytes(),
                tileTtlSeconds,
                sharedExecutor,
                false
            )
            : null;
        EcoregionTileService ecoregion = EcoregionTileService.create(
            gameDir,
            profile.biomesBaseUrl(),
            runtimeConfig.ecoregionTiles(),
            budgets.ecoregionBytes(),
            tileTtlSeconds,
            sharedExecutor,
            false
        );
        SurfaceWaterTileService surfaceWater = SurfaceWaterTileService.create(
            gameDir,
            EarthGenConfig.waterSourceZoomForWorldZoom(profile.zoom()),
            profile.surfaceWaterBaseUrl(),
            runtimeConfig.surfaceWaterTiles(),
            budgets.surfaceWaterBytes(),
            tileTtlSeconds,
            sharedExecutor,
            false
        );

        LOGGER.info(
            "[TX-WORLDGEN] memory budgets total_mb={} tile_mb={} snapshot_mb={} tile_split_bytes=(terrain={}, recovery={}, surface_water={}, ecoregion={}) current_tile_weights=(terrain={}, recovery={}, surface_water={}, ecoregion={}) tile_ttl_s={} snapshot_ttl_s={} shared_tile_threads={}",
            runtimeConfig.totalBudgetMb(),
            runtimeConfig.tileBudgetBytes() / (1024L * 1024L),
            runtimeConfig.snapshotBudgetBytes() / (1024L * 1024L),
            budgets.terrainBytes(),
            budgets.recoveryBytes(),
            budgets.surfaceWaterBytes(),
            budgets.ecoregionBytes(),
            terrain.currentMemoryCacheWeightBytes(),
            recovery != null ? recovery.currentMemoryCacheWeightBytes() : 0L,
            surfaceWater.currentMemoryCacheWeightBytes(),
            ecoregion.currentMemoryCacheWeightBytes(),
            runtimeConfig.tileTtlSeconds(),
            runtimeConfig.snapshotTtlSeconds(),
            runtimeConfig.sharedTileThreads()
        );

        return new EarthRuntimeServices(terrain, recovery, ecoregion, surfaceWater, sharedExecutor, true);
    }

    EarthRuntimeServices forZoom(Path gameDir, EarthGenerationProfile profile, TerrariumRuntimeConfig runtimeConfig) {
        return create(gameDir, profile, runtimeConfig);
    }

    EarthRuntimeServices withOverrides(Overrides overrides) {
        if (overrides == null || !overrides.hasAny()) {
            return this;
        }
        return new EarthRuntimeServices(
            overrides.tileService() != null ? overrides.tileService() : tileService,
            overrides.recoveryTileService() != null ? overrides.recoveryTileService() : recoveryTileService,
            overrides.ecoregionTileService() != null ? overrides.ecoregionTileService() : ecoregionTileService,
            overrides.surfaceWaterTileService() != null ? overrides.surfaceWaterTileService() : surfaceWaterTileService,
            sharedTileExecutor,
            ownsSharedTileExecutor
        );
    }

    TerrariumTileService tileService() {
        return tileService;
    }

    TerrariumTileService recoveryTileService() {
        return recoveryTileService;
    }

    EcoregionTileService ecoregionTileService() {
        return ecoregionTileService;
    }

    SurfaceWaterTileService surfaceWaterTileService() {
        return surfaceWaterTileService;
    }

    void closeAll() {
        close(tileService);
        close(recoveryTileService);
        close(ecoregionTileService);
        close(surfaceWaterTileService);
        closeSharedExecutor();
    }

    void closeReplacedBy(EarthRuntimeServices replacement) {
        if (replacement == null) {
            closeAll();
            return;
        }
        closeIfReplaced(tileService, replacement.tileService);
        closeIfReplaced(recoveryTileService, replacement.recoveryTileService);
        closeIfReplaced(ecoregionTileService, replacement.ecoregionTileService);
        closeIfReplaced(surfaceWaterTileService, replacement.surfaceWaterTileService);
        closeSharedExecutorIfReplaced(replacement.sharedTileExecutor);
    }

    private void closeSharedExecutorIfReplaced(ExecutorService replacement) {
        if (!ownsSharedTileExecutor || sharedTileExecutor == null || sharedTileExecutor == replacement) {
            return;
        }
        closeExecutor(sharedTileExecutor);
    }

    private void closeSharedExecutor() {
        if (!ownsSharedTileExecutor || sharedTileExecutor == null) {
            return;
        }
        closeExecutor(sharedTileExecutor);
    }

    private static void closeIfReplaced(AutoCloseable current, AutoCloseable replacement) {
        if (current == null || current == replacement) {
            return;
        }
        close(current);
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Service close failures should not block runtime context transitions.
        }
    }

    private static void closeExecutor(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static TileLayerBudgets allocateTileLayerBudgets(long totalTileBudgetBytes, boolean includeRecovery) {
        long total = Math.max(4L, totalTileBudgetBytes);
        int totalWeight = TERRAIN_WEIGHT + SURFACE_WATER_WEIGHT + ECOREGION_WEIGHT + (includeRecovery ? RECOVERY_WEIGHT : 0);
        long unit = Math.max(1L, total / Math.max(1, totalWeight));

        long terrain = Math.max(1L, unit * TERRAIN_WEIGHT);
        long recovery = includeRecovery ? Math.max(1L, unit * RECOVERY_WEIGHT) : 0L;
        long surfaceWater = Math.max(1L, unit * SURFACE_WATER_WEIGHT);
        long ecoregion = Math.max(1L, unit * ECOREGION_WEIGHT);
        long allocated = terrain + recovery + surfaceWater + ecoregion;
        if (allocated < total) {
            terrain += total - allocated;
        }
        return new TileLayerBudgets(terrain, recovery, surfaceWater, ecoregion);
    }

    private record TileLayerBudgets(long terrainBytes, long recoveryBytes, long surfaceWaterBytes, long ecoregionBytes) {
    }

    record Overrides(
        TerrariumTileService tileService,
        TerrariumTileService recoveryTileService,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService
    ) {
        private static final Overrides NONE = new Overrides(null, null, null, null);

        static Overrides none() {
            return NONE;
        }

        boolean hasAny() {
            return tileService != null
                || recoveryTileService != null
                || ecoregionTileService != null
                || surfaceWaterTileService != null;
        }
    }
}
