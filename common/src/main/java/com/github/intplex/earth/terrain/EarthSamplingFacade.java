package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EarthSamplingFacade {
    private static final String ERROR_ECOREGION_TILE_LOAD_FAILURE = errorLabel("ecoregion_tile", "load_failure");
    private static final String ERROR_SURFACE_WATER_MISSING = errorLabel("surface_water", "missing");
    private static final ThreadLocal<ChunkCacheState> CHUNK_LOCAL_CACHES = ThreadLocal.withInitial(ChunkCacheState::new);

    private EarthSamplingFacade() {
    }

    public static EarthSamplingResult.TerrainProbe sampleTerrain(
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        return sampleTerrain(TerrainServices.requireContext(), blockX, blockZ, localCaches);
    }

    static EarthSamplingResult.TerrainProbe sampleTerrain(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        return sampleTerrainInternal(context, blockX, blockZ, false, 0, localCaches);
    }

    public static EarthSamplingResult.TerrainProbe sampleTerrainWithEcoregionColorHint(
        int blockX,
        int blockZ,
        int ecoregionColorRgb,
        LocalTileCaches localCaches
    ) {
        return sampleTerrainInternal(
            TerrainServices.requireContext(),
            blockX,
            blockZ,
            true,
            ecoregionColorRgb,
            localCaches
        );
    }

    public static EarthSamplingResult.EcoregionProbe sampleEcoregionColor(
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        return sampleEcoregionColor(TerrainServices.requireContext(), blockX, blockZ, localCaches);
    }

    static EarthSamplingResult.EcoregionProbe sampleEcoregionColor(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        int zoom = context.profile().zoom();
        if (!TileProjection.projectEcoregion(blockX, blockZ, zoom, localCaches.ecoregionPoint())) {
            return EarthSamplingResult.EcoregionProbe.outOfBounds();
        }

        TileKey tileKey = localCaches.ecoregionPoint().tileKey();
        int pixelX = localCaches.ecoregionPoint().pixelX();
        int pixelY = localCaches.ecoregionPoint().pixelY();

        EcoregionTile tile = ecoregionTileFromCacheOrLoad(localCaches, context, runtimeState, tileKey, blockX, blockZ);
        if (tile == null) {
            return new EarthSamplingResult.EcoregionProbe(
                EarthSamplingResult.EcoregionStatus.TILE_LOAD_FAILURE,
                0,
                tileKey,
                pixelX,
                pixelY,
                ERROR_ECOREGION_TILE_LOAD_FAILURE
            );
        }

        int colorRgb = tile.sampleColorRgb(pixelX, pixelY);
        return new EarthSamplingResult.EcoregionProbe(
            EarthSamplingResult.EcoregionStatus.SAMPLED,
            colorRgb,
            tileKey,
            pixelX,
            pixelY,
            null
        );
    }

    public static EarthSamplingResult.SurfaceWaterProbe sampleSurfaceWater(
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        return sampleSurfaceWater(TerrainServices.requireContext(), blockX, blockZ, localCaches);
    }

    static EarthSamplingResult.SurfaceWaterProbe sampleSurfaceWater(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        int waterZoom = EarthGenConfig.waterSourceZoomForWorldZoom(context.profile().zoom());
        if (!TileProjection.projectSurfaceWater(blockX, blockZ, waterZoom, localCaches.surfaceWaterPoint())) {
            return EarthSamplingResult.SurfaceWaterProbe.OUT_OF_BOUNDS;
        }

        TileKey waterTileKey = localCaches.surfaceWaterPoint().tileKey();
        int pixelX = localCaches.surfaceWaterPoint().pixelX();
        int pixelY = localCaches.surfaceWaterPoint().pixelY();
        SurfaceWaterTileLookup tileLookup = surfaceWaterTileLookupFromCacheOrLoad(
            localCaches,
            context,
            runtimeState,
            waterTileKey,
            waterZoom,
            blockX,
            blockZ
        );

        SurfaceWaterTile waterTile = tileLookup.tile();
        boolean isWater = waterTile != null
            && waterTile.isWaterAt(pixelX, pixelY, runtimeState.inlandWaterSettings().minWaterMonths());
        return new EarthSamplingResult.SurfaceWaterProbe(
            tileLookup.status,
            isWater,
            tileLookup.dataAvailable(),
            waterTileKey,
            pixelX,
            pixelY,
            tileLookup.error
        );
    }

    static LocalTileCaches chunkLocalCaches() {
        ChunkCacheState state = CHUNK_LOCAL_CACHES.get();
        long generation = TerrainServices.runtimeGeneration();
        if (state.runtimeGeneration() != generation) {
            state.caches().clear();
            state.setRuntimeGeneration(generation);
        }
        return state.caches();
    }

    private static EarthSamplingResult.TerrainProbe sampleTerrainInternal(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        boolean hasEcoregionColorHint,
        int ecoregionColorHint,
        LocalTileCaches localCaches
    ) {
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        int zoom = context.profile().zoom();
        if (!TileProjection.projectTerrain(blockX, blockZ, zoom, localCaches.terrainPoint())) {
            return EarthSamplingResult.TerrainProbe.OUT_OF_BOUNDS;
        }

        TileKey terrainTileKey = localCaches.terrainPoint().tileKey();
        int terrainPixelX = TerrariumSeamPatch.patchedPixelX(zoom, terrainTileKey, localCaches.terrainPoint().pixelX());
        int terrainPixelY = localCaches.terrainPoint().pixelY();

        TerrariumTile terrainTile = terrainTileFromCacheOrLoad(localCaches, context, runtimeState, terrainTileKey, blockX, blockZ);
        boolean terrainSampleAvailable = terrainTile != null;
        double meters = terrainSampleAvailable ? terrainTile.sampleMeters(terrainPixelX, terrainPixelY) : 0.0;

        boolean needsSurfaceWaterForRecovery = OceanBathymetryRecovery.shouldAttemptRecovery(zoom, terrainSampleAvailable, meters);
        boolean needsSurfaceWaterForInlandAnalysis = runtimeState.inlandWaterSettings().enabled();
        EarthSamplingResult.SurfaceWaterProbe surfaceWaterProbe = EarthSamplingResult.SurfaceWaterProbe.NOT_REQUESTED;

        if (needsSurfaceWaterForRecovery || needsSurfaceWaterForInlandAnalysis) {
            surfaceWaterProbe = sampleSurfaceWater(context, blockX, blockZ, localCaches);
        }

        if (needsSurfaceWaterForRecovery) {
            final EarthSamplingResult.SurfaceWaterProbe finalSurfaceWaterProbe = surfaceWaterProbe;
            meters = TerrainBathymetryRecovery.applyIfEligible(
                blockX,
                blockZ,
                zoom,
                terrainSampleAvailable,
                meters,
                () -> resolveEcoregionGate(context, runtimeState, blockX, blockZ, hasEcoregionColorHint, ecoregionColorHint, localCaches),
                finalSurfaceWaterProbe::isWater,
                (recoveryTileKey, localX, localY) -> {
                    TerrariumTile recoveryTile = recoveryTerrainTileFromCacheOrLoad(
                        localCaches,
                        context,
                        runtimeState,
                        recoveryTileKey,
                        blockX,
                        blockZ
                    );
                    if (recoveryTile == null) {
                        return null;
                    }
                    return recoveryTile.sampleMeters(localX, localY);
                }
            );
        }

        int terrainY = EarthGenConfig.mapMetersToTerrainY(meters);
        boolean ocean = meters <= 0.0;
        if (runtimeState.inlandWaterSettings().enabled()) {
            return new EarthSamplingResult.TerrainProbe(
                true,
                terrainY,
                ocean,
                surfaceWaterProbe.isWater(),
                surfaceWaterProbe.dataAvailable()
            );
        }
        return new EarthSamplingResult.TerrainProbe(true, terrainY, ocean, false, true);
    }

    private static TerrainBathymetryRecovery.EcoregionGate resolveEcoregionGate(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        int blockX,
        int blockZ,
        boolean hasEcoregionColorHint,
        int ecoregionColorHint,
        LocalTileCaches localCaches
    ) {
        if (hasEcoregionColorHint) {
            return OceanBathymetryRecovery.isEcoregionNoDataColor(ecoregionColorHint)
                ? TerrainBathymetryRecovery.EcoregionGate.NO_DATA
                : TerrainBathymetryRecovery.EcoregionGate.NOT_NO_DATA;
        }
        int zoom = context.profile().zoom();
        if (!TileProjection.projectEcoregion(blockX, blockZ, zoom, localCaches.ecoregionPoint())) {
            return TerrainBathymetryRecovery.EcoregionGate.UNAVAILABLE;
        }
        TileKey ecoregionTileKey = localCaches.ecoregionPoint().tileKey();
        EcoregionTile ecoregionTile = ecoregionTileFromCacheOrLoad(localCaches, context, runtimeState, ecoregionTileKey, blockX, blockZ);
        if (ecoregionTile == null) {
            return TerrainBathymetryRecovery.EcoregionGate.UNAVAILABLE;
        }
        int sampledRgb = ecoregionTile.sampleColorRgb(localCaches.ecoregionPoint().pixelX(), localCaches.ecoregionPoint().pixelY());
        return OceanBathymetryRecovery.isEcoregionNoDataColor(sampledRgb)
            ? TerrainBathymetryRecovery.EcoregionGate.NO_DATA
            : TerrainBathymetryRecovery.EcoregionGate.NOT_NO_DATA;
    }

    private static TerrariumTile terrainTileFromCacheOrLoad(
        LocalTileCaches localCaches,
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        return getCachedTile(
            localCaches.primaryTerrainTiles,
            tileKey,
            () -> loadTerrainTile(context, runtimeState, tileKey, blockX, blockZ)
        );
    }

    private static TerrariumTile recoveryTerrainTileFromCacheOrLoad(
        LocalTileCaches localCaches,
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        return getCachedTile(
            localCaches.recoveryTerrainTiles,
            tileKey,
            () -> loadRecoveryTerrainTile(context, runtimeState, tileKey, blockX, blockZ)
        );
    }

    private static EcoregionTile ecoregionTileFromCacheOrLoad(
        LocalTileCaches localCaches,
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        return getCachedTile(
            localCaches.ecoregionTiles,
            tileKey,
            () -> loadEcoregionTile(context, runtimeState, tileKey, blockX, blockZ)
        );
    }

    private static SurfaceWaterTileLookup surfaceWaterTileLookupFromCacheOrLoad(
        LocalTileCaches localCaches,
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int waterZoom,
        int blockX,
        int blockZ
    ) {
        SurfaceWaterTileLookup cached = localCaches.surfaceWaterTiles.get(tileKey);
        if (cached != null) {
            return cached;
        }
        SurfaceWaterTileLookup loaded = loadSurfaceWaterTile(context, runtimeState, tileKey, waterZoom, blockX, blockZ);
        localCaches.surfaceWaterTiles.put(tileKey, loaded);
        return loaded;
    }

    private static <T> T getCachedTile(
        Map<TileKey, T> cache,
        TileKey tileKey,
        Loader<T> loader
    ) {
        T cached = cache.get(tileKey);
        if (cached != null) {
            return cached;
        }

        T loaded = loader.load();
        if (loaded != null) {
            cache.put(tileKey, loaded);
        }
        return loaded;
    }

    private static TerrariumTile loadTerrainTile(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        try {
            return context.services().tileService().requireTile(tileKey);
        } catch (RuntimeException exception) {
            if (runtimeState.loggedTerrainTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Terrain tile fetch/decode failure: {} context={}",
                    exception.toString(),
                    TerrainService.sampleContextLabel(tileKey, blockX, blockZ)
                );
            }
            return null;
        }
    }

    private static TerrariumTile loadRecoveryTerrainTile(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        try {
            return context.services().recoveryTileService().requireTile(tileKey);
        } catch (RuntimeException exception) {
            if (runtimeState.loggedTerrainTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Recovery terrain tile fetch/decode failure: {} context={}",
                    exception.toString(),
                    TerrainService.sampleContextLabel(tileKey, blockX, blockZ)
                );
            }
            return null;
        }
    }

    private static EcoregionTile loadEcoregionTile(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        try {
            return context.services().ecoregionTileService().requireTile(tileKey);
        } catch (RuntimeException exception) {
            if (runtimeState.loggedEcoregionTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Ecoregion tile fetch/decode failure: {} context={}",
                    exception.toString(),
                    TerrainService.sampleContextLabel(tileKey, blockX, blockZ)
                );
            }
            return null;
        }
    }

    private static SurfaceWaterTileLookup loadSurfaceWaterTile(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        TileKey tileKey,
        int waterZoom,
        int blockX,
        int blockZ
    ) {
        if (!runtimeState.surfaceWaterCoverageSettings().intersectsTile(tileKey, waterZoom)) {
            return SurfaceWaterTileLookup.OUTSIDE_COVERAGE;
        }
        if (runtimeState.knownMissingSurfaceWaterTiles().contains(tileKey)) {
            return SurfaceWaterTileLookup.MISSING;
        }
        try {
            return SurfaceWaterTileLookup.available(context.services().surfaceWaterTileService().requireTile(tileKey));
        } catch (RemotePngTileStore.MissingTileException ignored) {
            runtimeState.knownMissingSurfaceWaterTiles().markIfNew(tileKey);
            return SurfaceWaterTileLookup.MISSING;
        } catch (RuntimeException exception) {
            if (runtimeState.loggedFailedSurfaceWaterTiles().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Surface water tile fetch/decode failure status={} error={} context={}",
                    EarthSamplingResult.SurfaceWaterStatus.FAILED,
                    exception.toString(),
                    TerrainService.sampleContextLabel(tileKey, blockX, blockZ)
                );
            }
            return SurfaceWaterTileLookup.failed(exception.toString());
        }
    }

    private record SurfaceWaterTileLookup(EarthSamplingResult.SurfaceWaterStatus status, SurfaceWaterTile tile, String error) {
        private static final SurfaceWaterTileLookup MISSING =
            new SurfaceWaterTileLookup(EarthSamplingResult.SurfaceWaterStatus.MISSING, null, ERROR_SURFACE_WATER_MISSING);
        private static final SurfaceWaterTileLookup OUTSIDE_COVERAGE =
            new SurfaceWaterTileLookup(EarthSamplingResult.SurfaceWaterStatus.OUTSIDE_COVERAGE, null, null);

        private static SurfaceWaterTileLookup available(SurfaceWaterTile tile) {
            return new SurfaceWaterTileLookup(EarthSamplingResult.SurfaceWaterStatus.AVAILABLE, tile, null);
        }

        private static SurfaceWaterTileLookup failed(String error) {
            return new SurfaceWaterTileLookup(EarthSamplingResult.SurfaceWaterStatus.FAILED, null, error);
        }

        private boolean dataAvailable() {
            return status == EarthSamplingResult.SurfaceWaterStatus.AVAILABLE && tile != null;
        }
    }

    private static String errorLabel(String layer, String reason) {
        return layer + "_" + reason;
    }

    @FunctionalInterface
    private interface Loader<T> {
        T load();
    }

    public static final class LocalTileCaches {
        private final Map<TileKey, TerrariumTile> primaryTerrainTiles;
        private final Map<TileKey, TerrariumTile> recoveryTerrainTiles;
        private final Map<TileKey, EcoregionTile> ecoregionTiles;
        private final Map<TileKey, SurfaceWaterTileLookup> surfaceWaterTiles;

        private final TileProjection.MutablePoint terrainPoint = new TileProjection.MutablePoint();
        private final TileProjection.MutablePoint ecoregionPoint = new TileProjection.MutablePoint();
        private final TileProjection.MutablePoint surfaceWaterPoint = new TileProjection.MutablePoint();

        private LocalTileCaches(int maxEntriesPerLayer) {
            this.primaryTerrainTiles = newLruTileMap(maxEntriesPerLayer);
            this.recoveryTerrainTiles = newLruTileMap(maxEntriesPerLayer);
            this.ecoregionTiles = newLruTileMap(maxEntriesPerLayer);
            this.surfaceWaterTiles = newLruTileMap(maxEntriesPerLayer);
        }

        public static LocalTileCaches hotPathCaches() {
            return new LocalTileCaches(8);
        }

        public void clear() {
            primaryTerrainTiles.clear();
            recoveryTerrainTiles.clear();
            ecoregionTiles.clear();
            surfaceWaterTiles.clear();
        }

        TileProjection.MutablePoint terrainPoint() {
            return terrainPoint;
        }

        TileProjection.MutablePoint ecoregionPoint() {
            return ecoregionPoint;
        }

        TileProjection.MutablePoint surfaceWaterPoint() {
            return surfaceWaterPoint;
        }

        private static <T> Map<TileKey, T> newLruTileMap(int maxEntries) {
            int boundedMaxEntries = Math.max(1, maxEntries);
            return new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<TileKey, T> eldest) {
                    return size() > boundedMaxEntries;
                }
            };
        }
    }

    private static final class ChunkCacheState {
        private final LocalTileCaches caches = new LocalTileCaches(128);
        private long runtimeGeneration = Long.MIN_VALUE;

        private LocalTileCaches caches() {
            return caches;
        }

        private long runtimeGeneration() {
            return runtimeGeneration;
        }

        private void setRuntimeGeneration(long runtimeGeneration) {
            this.runtimeGeneration = runtimeGeneration;
        }
    }
}
