package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EarthRuntimeServices {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final EarthRuntimeServices EMPTY = new EarthRuntimeServices(null, Map.of(), null, null, null, false);
    private static final int TERRAIN_WEIGHT = 3;
    private static final int SUPPLEMENTAL_TERRAIN_WEIGHT = 2;
    private static final int SURFACE_WATER_WEIGHT = 3;
    private static final int ECOREGION_WEIGHT = 1;

    private final TerrariumTileService tileService;
    private final Map<Integer, TerrariumTileService> supplementalTerrainTileServices;
    private final EcoregionTileService ecoregionTileService;
    private final SurfaceWaterTileService surfaceWaterTileService;
    private final ExecutorService sharedTileExecutor;
    private final boolean ownsSharedTileExecutor;

    EarthRuntimeServices(
        TerrariumTileService tileService,
        Map<Integer, TerrariumTileService> supplementalTerrainTileServices,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService,
        ExecutorService sharedTileExecutor,
        boolean ownsSharedTileExecutor
    ) {
        this.tileService = tileService;
        this.supplementalTerrainTileServices = supplementalTerrainTileServices == null
            ? Map.of()
            : Map.copyOf(supplementalTerrainTileServices);
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
        Set<Integer> requiredSourceZooms = requiredSupplementalSourceZooms(profile.zoom());
        boolean includeSupplementalTerrain = !requiredSourceZooms.isEmpty();
        TileLayerBudgets budgets = allocateTileLayerBudgets(runtimeConfig.tileBudgetBytes(), includeSupplementalTerrain);
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
        Map<Integer, TerrariumTileService> supplementalTerrain = createSupplementalTerrainServices(
            gameDir,
            profile.terrainBaseUrl(),
            runtimeConfig,
            budgets.recoveryBytes(),
            tileTtlSeconds,
            sharedExecutor,
            requiredSourceZooms
        );
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
        long supplementalCurrentWeight = supplementalTerrain.values()
            .stream()
            .mapToLong(TerrariumTileService::currentMemoryCacheWeightBytes)
            .sum();

        LOGGER.info(
            "[TX-WORLDGEN] memory budgets total_mb={} tile_mb={} snapshot_mb={} tile_split_bytes=(terrain={}, recovery={}, surface_water={}, ecoregion={}) current_tile_weights=(terrain={}, recovery={}, surface_water={}, ecoregion={}) recovery_source_zooms={} tile_ttl_s={} snapshot_ttl_s={} shared_tile_threads={}",
            runtimeConfig.totalBudgetMb(),
            runtimeConfig.tileBudgetBytes() / (1024L * 1024L),
            runtimeConfig.snapshotBudgetBytes() / (1024L * 1024L),
            budgets.terrainBytes(),
            budgets.recoveryBytes(),
            budgets.surfaceWaterBytes(),
            budgets.ecoregionBytes(),
            terrain.currentMemoryCacheWeightBytes(),
            supplementalCurrentWeight,
            surfaceWater.currentMemoryCacheWeightBytes(),
            ecoregion.currentMemoryCacheWeightBytes(),
            requiredSourceZooms,
            runtimeConfig.tileTtlSeconds(),
            runtimeConfig.snapshotTtlSeconds(),
            runtimeConfig.sharedTileThreads()
        );

        return new EarthRuntimeServices(terrain, supplementalTerrain, ecoregion, surfaceWater, sharedExecutor, true);
    }

    EarthRuntimeServices forZoom(Path gameDir, EarthGenerationProfile profile, TerrariumRuntimeConfig runtimeConfig) {
        return create(gameDir, profile, runtimeConfig);
    }

    EarthRuntimeServices withOverrides(Overrides overrides) {
        if (overrides == null || !overrides.hasAny()) {
            return this;
        }
        Map<Integer, TerrariumTileService> nextSupplemental = supplementalTerrainTileServices;
        TerrariumTileService overrideSupplemental = overrides.recoveryTileService();
        if (overrideSupplemental != null) {
            nextSupplemental = new HashMap<>(supplementalTerrainTileServices);
            nextSupplemental.put(overrideSupplemental.zoom(), overrideSupplemental);
            nextSupplemental = Map.copyOf(nextSupplemental);
        }
        return new EarthRuntimeServices(
            overrides.tileService() != null ? overrides.tileService() : tileService,
            nextSupplemental,
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
        return supplementalTerrainTileServices.get(OceanBathymetryRecovery.SOURCE_ZOOM);
    }

    TerrariumTileService terrainSourceTileService(int zoom) {
        int validatedZoom = EarthGenConfig.validateZoom(zoom);
        if (tileService != null && tileService.zoom() == validatedZoom) {
            return tileService;
        }
        return supplementalTerrainTileServices.get(validatedZoom);
    }

    EcoregionTileService ecoregionTileService() {
        return ecoregionTileService;
    }

    SurfaceWaterTileService surfaceWaterTileService() {
        return surfaceWaterTileService;
    }

    void closeAll() {
        close(tileService);
        closeSupplementalTerrainServices(supplementalTerrainTileServices, tileService, null);
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
        closeSupplementalTerrainServices(
            supplementalTerrainTileServices,
            tileService,
            replacement.tileService,
            replacement.supplementalTerrainTileServices
        );
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

    private static void closeSupplementalTerrainServices(
        Map<Integer, TerrariumTileService> services,
        TerrariumTileService currentPrimary,
        TerrariumTileService replacementPrimary
    ) {
        closeSupplementalTerrainServices(services, currentPrimary, replacementPrimary, Map.of());
    }

    private static void closeSupplementalTerrainServices(
        Map<Integer, TerrariumTileService> services,
        TerrariumTileService currentPrimary,
        TerrariumTileService replacementPrimary,
        Map<Integer, TerrariumTileService> replacementServices
    ) {
        for (Map.Entry<Integer, TerrariumTileService> entry : services.entrySet()) {
            TerrariumTileService current = entry.getValue();
            TerrariumTileService replacement = replacementServices.get(entry.getKey());
            if (current == null || current == replacement || current == replacementPrimary || current == currentPrimary) {
                continue;
            }
            close(current);
        }
    }

    private static TileLayerBudgets allocateTileLayerBudgets(long totalTileBudgetBytes, boolean includeSupplementalTerrain) {
        long total = Math.max(4L, totalTileBudgetBytes);
        int totalWeight = TERRAIN_WEIGHT
            + SURFACE_WATER_WEIGHT
            + ECOREGION_WEIGHT
            + (includeSupplementalTerrain ? SUPPLEMENTAL_TERRAIN_WEIGHT : 0);
        long unit = Math.max(1L, total / Math.max(1, totalWeight));

        long terrain = Math.max(1L, unit * TERRAIN_WEIGHT);
        long recovery = includeSupplementalTerrain ? Math.max(1L, unit * SUPPLEMENTAL_TERRAIN_WEIGHT) : 0L;
        long surfaceWater = Math.max(1L, unit * SURFACE_WATER_WEIGHT);
        long ecoregion = Math.max(1L, unit * ECOREGION_WEIGHT);
        long allocated = terrain + recovery + surfaceWater + ecoregion;
        if (allocated < total) {
            terrain += total - allocated;
        }
        return new TileLayerBudgets(terrain, recovery, surfaceWater, ecoregion);
    }

    private static Set<Integer> requiredSupplementalSourceZooms(int worldZoom) {
        int validatedWorldZoom = EarthGenConfig.validateZoom(worldZoom);
        Set<Integer> sourceZooms = new TreeSet<>(BadTerrainTileRegistry.sourceZoomsForTargetZoom(validatedWorldZoom));
        if (OceanBathymetryRecovery.isRecoveryActiveForZoom(validatedWorldZoom)) {
            sourceZooms.add(OceanBathymetryRecovery.SOURCE_ZOOM);
        }
        sourceZooms.remove(validatedWorldZoom);
        return Set.copyOf(sourceZooms);
    }

    private static Map<Integer, TerrariumTileService> createSupplementalTerrainServices(
        Path gameDir,
        String baseUrl,
        TerrariumRuntimeConfig runtimeConfig,
        long totalBudgetBytes,
        int tileTtlSeconds,
        ExecutorService sharedExecutor,
        Set<Integer> sourceZooms
    ) {
        if (sourceZooms.isEmpty()) {
            return Map.of();
        }

        long[] perServiceBudgets = splitBudgetEvenly(totalBudgetBytes, sourceZooms.size());
        Map<Integer, TerrariumTileService> services = new LinkedHashMap<>();
        int budgetIndex = 0;
        for (int sourceZoom : sourceZooms) {
            long memoryBudget = perServiceBudgets[budgetIndex++];
            services.put(
                sourceZoom,
                TerrariumTileService.create(
                    gameDir,
                    sourceZoom,
                    baseUrl,
                    runtimeConfig.recoveryTiles(),
                    memoryBudget,
                    tileTtlSeconds,
                    sharedExecutor,
                    false
                )
            );
        }
        return Map.copyOf(services);
    }

    private static long[] splitBudgetEvenly(long totalBudgetBytes, int count) {
        if (count <= 0) {
            return new long[0];
        }
        long[] allocations = new long[count];
        long normalizedTotal = Math.max(1L, totalBudgetBytes);
        if (normalizedTotal < count) {
            for (int index = 0; index < count; index++) {
                allocations[index] = 1L;
            }
            return allocations;
        }
        long base = normalizedTotal / count;
        long remainder = normalizedTotal % count;
        for (int index = 0; index < count; index++) {
            allocations[index] = base + (index < remainder ? 1L : 0L);
        }
        return allocations;
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
