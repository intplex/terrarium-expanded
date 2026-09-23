package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class EarthSamplingFacade {
    private static final String ERROR_SURFACE_WATER_MISSING = errorLabel("surface_water", "missing");
    private static final ThreadLocal<ChunkCacheState> CHUNK_LOCAL_CACHES = ThreadLocal.withInitial(ChunkCacheState::new);
    private static final ThreadLocal<MutableTerrainProbe> TERRAIN_PROBE_SCRATCH =
        ThreadLocal.withInitial(MutableTerrainProbe::new);

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
        MutableTerrainProbe mutableProbe = TERRAIN_PROBE_SCRATCH.get();
        sampleTerrainInternal(context, blockX, blockZ, false, 0, localCaches, mutableProbe);
        return mutableProbe.toProbe();
    }

    public static EarthSamplingResult.TerrainProbe sampleTerrainWithEcoregionColorHint(
        int blockX,
        int blockZ,
        int ecoregionColorRgb,
        LocalTileCaches localCaches
    ) {
        MutableTerrainProbe mutableProbe = TERRAIN_PROBE_SCRATCH.get();
        sampleTerrainInternal(
            TerrainServices.requireContext(),
            blockX,
            blockZ,
            true,
            ecoregionColorRgb,
            localCaches,
            mutableProbe
        );
        return mutableProbe.toProbe();
    }

    static void sampleTerrainInto(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        LocalTileCaches localCaches,
        MutableTerrainProbe out
    ) {
        sampleTerrainInternal(context, blockX, blockZ, false, 0, localCaches, out);
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
        EarthRuntimeContext context = TerrainServices.requireContext();
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        long generation = TerrainServices.runtimeGeneration();
        long nowNanos = System.nanoTime();
        if (state.runtimeGeneration() != generation) {
            state.recreateCaches(runtimeState.chunkLocalCacheEntries());
            state.setRuntimeGeneration(generation);
        } else if (state.maxEntriesPerLayer() != runtimeState.chunkLocalCacheEntries()) {
            state.recreateCaches(runtimeState.chunkLocalCacheEntries());
        } else if (isIdleExpired(state.lastAccessNanos(), runtimeState.threadLocalIdleSeconds(), nowNanos)) {
            state.caches().clear();
        }
        state.setLastAccessNanos(nowNanos);
        return state.caches();
    }

    private static void sampleTerrainInternal(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        boolean hasEcoregionColorHint,
        int ecoregionColorHint,
        LocalTileCaches localCaches,
        MutableTerrainProbe out
    ) {
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        int zoom = context.profile().zoom();
        if (!TileProjection.projectTerrain(blockX, blockZ, zoom, localCaches.terrainPoint())) {
            out.set(false, EarthGenConfig.MIN_Y, false, false, true);
            return;
        }

        TileKey terrainTileKey = localCaches.terrainPoint().tileKey();
        int terrainPixelX = TerrariumSeamPatch.patchedPixelX(zoom, terrainTileKey, localCaches.terrainPoint().pixelX());
        int terrainPixelY = localCaches.terrainPoint().pixelY();

        TerrariumTile terrainTile = terrainTileFromCacheOrLoad(localCaches, context, runtimeState, terrainTileKey, blockX, blockZ);
        double meters = terrainTile.sampleMeters(terrainPixelX, terrainPixelY);
        OptionalInt sourceZoomOverride = BadTerrainTileRegistry.sourceZoomFor(zoom, terrainTileKey);
        if (sourceZoomOverride.isPresent() && meters <= 0.0) {
            int sourceZoom = sourceZoomOverride.getAsInt();
            OptionalDouble replacementMeters = OceanBathymetryRecovery.sampleBilinearMeters(
                blockX,
                blockZ,
                zoom,
                sourceZoom,
                (sourceTileKey, localX, localY) -> {
                    TerrariumTile sourceTile = sourceTerrainTileFromCacheOrLoad(
                        localCaches,
                        context,
                        runtimeState,
                        sourceZoom,
                        sourceTileKey,
                        blockX,
                        blockZ
                    );
                    return sourceTile.sampleMeters(localX, localY);
                },
                OceanBathymetryRecovery.InterpolationClamp.OCEAN_ONLY
            );
            if (replacementMeters.isPresent()) {
                meters = replacementMeters.getAsDouble();
            } else {
                BadTerrainTileRegistry.TargetTile targetTile = new BadTerrainTileRegistry.TargetTile(zoom, terrainTileKey);
                if (runtimeState.loggedBadTerrainReplacementFailures().markIfNew(targetTile)) {
                    TerrainService.logWarn(
                        "Bad terrain tile replacement failed target={} source_zoom={} context={}",
                        targetTile,
                        sourceZoom,
                        TerrainService.sampleContextLabel(terrainTileKey, blockX, blockZ)
                    );
                }
                throw new IllegalStateException("Required terrain replacement unavailable: " + targetTile + " source_zoom=" + sourceZoom);
            }
        }

        boolean needsSurfaceWaterForRecovery = OceanBathymetryRecovery.shouldAttemptRecovery(zoom, meters);
        boolean needsSurfaceWaterForInlandAnalysis = runtimeState.inlandWaterSettings().enabled();
        boolean surfaceWaterIsWater = false;
        boolean surfaceWaterDataAvailable = true;

        if (needsSurfaceWaterForRecovery || needsSurfaceWaterForInlandAnalysis) {
            SurfaceWaterTerrainState state = sampleSurfaceWaterForTerrain(context, blockX, blockZ, localCaches);
            surfaceWaterIsWater = state.isWater();
            surfaceWaterDataAvailable = state.dataAvailable();
        }

        if (needsSurfaceWaterForRecovery) {
            final boolean finalSurfaceWaterIsWater = surfaceWaterIsWater;
            meters = TerrainBathymetryRecovery.applyIfEligible(
                blockX,
                blockZ,
                zoom,
                meters,
                () -> resolveEcoregionGate(context, runtimeState, blockX, blockZ, hasEcoregionColorHint, ecoregionColorHint, localCaches),
                () -> finalSurfaceWaterIsWater,
                localCaches.recoverySampleCache(),
                (sourceZoom, recoveryTileKey, localX, localY) -> {
                    TerrariumTile recoveryTile = sourceTerrainTileFromCacheOrLoad(
                        localCaches,
                        context,
                        runtimeState,
                        sourceZoom,
                        recoveryTileKey,
                        blockX,
                        blockZ
                    );
                    return recoveryTile.sampleMeters(localX, localY);
                }
            );
        }

        int terrainY = EarthGenConfig.mapMetersToTerrainY(meters);
        boolean ocean = meters <= 0.0;
        if (runtimeState.inlandWaterSettings().enabled()) {
            out.set(
                true,
                terrainY,
                ocean,
                surfaceWaterIsWater,
                surfaceWaterDataAvailable
            );
            return;
        }
        out.set(true, terrainY, ocean, false, true);
    }

    private static SurfaceWaterTerrainState sampleSurfaceWaterForTerrain(
        EarthRuntimeContext context,
        int blockX,
        int blockZ,
        LocalTileCaches localCaches
    ) {
        TerrainService.RuntimeState runtimeState = context.terrainRuntimeState();
        int waterZoom = EarthGenConfig.waterSourceZoomForWorldZoom(context.profile().zoom());
        if (!TileProjection.projectSurfaceWater(blockX, blockZ, waterZoom, localCaches.surfaceWaterPoint())) {
            return SurfaceWaterTerrainState.OUT_OF_BOUNDS;
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
        return new SurfaceWaterTerrainState(isWater, tileLookup.dataAvailable());
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

    private static TerrariumTile sourceTerrainTileFromCacheOrLoad(
        LocalTileCaches localCaches,
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        int sourceZoom,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        return getCachedTile(
            localCaches.sourceTerrainTiles,
            new SourceTileKey(sourceZoom, tileKey),
            () -> loadSourceTerrainTile(context, runtimeState, sourceZoom, tileKey, blockX, blockZ)
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

    private static <K, T> T getCachedTile(
        Map<K, T> cache,
        K key,
        Loader<T> loader
    ) {
        T cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        T loaded = loader.load();
        cache.put(key, loaded);
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
            String sampleContext = TerrainService.sampleContextLabel(tileKey, blockX, blockZ);
            if (runtimeState.loggedTerrainTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Required terrain tile fetch/decode failure; generation aborted: {} context={}",
                    exception.toString(),
                    sampleContext,
                    exception
                );
            }
            // Propagate through snapshot construction and the generation future.
            // A missing elevation must never become a cached sea-level surface.
            throw new IllegalStateException("Required terrain elevation unavailable; generation aborted: " + sampleContext, exception);
        }
    }

    private static TerrariumTile loadSourceTerrainTile(
        EarthRuntimeContext context,
        TerrainService.RuntimeState runtimeState,
        int sourceZoom,
        TileKey tileKey,
        int blockX,
        int blockZ
    ) {
        TerrariumTileService sourceService = context.services().terrainSourceTileService(sourceZoom);
        try {
            if (sourceService == null) {
                throw new IllegalStateException("Terrain source tile service unavailable at zoom " + sourceZoom);
            }
            return sourceService.requireTile(tileKey);
        } catch (RuntimeException exception) {
            String sampleContext = "source_zoom=" + sourceZoom + " " + TerrainService.sampleContextLabel(tileKey, blockX, blockZ);
            if (runtimeState.loggedTerrainTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Required terrain source tile fetch/decode failure; generation aborted: {} context={}",
                    exception.toString(),
                    sampleContext,
                    exception
                );
            }
            throw new IllegalStateException("Required terrain source elevation unavailable; generation aborted: " + sampleContext, exception);
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
            String sampleContext = TerrainService.sampleContextLabel(tileKey, blockX, blockZ);
            if (runtimeState.loggedEcoregionTileFailures().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Required ecoregion tile fetch/decode failure; generation aborted: {} context={}",
                    exception.toString(),
                    sampleContext,
                    exception
                );
            }
            throw new IllegalStateException("Required ecoregion data unavailable; generation aborted: " + sampleContext, exception);
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
            String sampleContext = TerrainService.sampleContextLabel(tileKey, blockX, blockZ);
            if (runtimeState.loggedFailedSurfaceWaterTiles().markIfNew(tileKey)) {
                TerrainService.logWarn(
                    "Required surface water tile fetch/decode failure; generation aborted: {} context={}",
                    exception.toString(),
                    sampleContext,
                    exception
                );
            }
            throw new IllegalStateException("Required surface water data unavailable; generation aborted: " + sampleContext, exception);
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

        private boolean dataAvailable() {
            return status == EarthSamplingResult.SurfaceWaterStatus.AVAILABLE && tile != null;
        }
    }

    private record SurfaceWaterTerrainState(boolean isWater, boolean dataAvailable) {
        private static final SurfaceWaterTerrainState OUT_OF_BOUNDS = new SurfaceWaterTerrainState(false, false);
    }

    private static String errorLabel(String layer, String reason) {
        return layer + "_" + reason;
    }

    @FunctionalInterface
    private interface Loader<T> {
        T load();
    }

    static final class MutableTerrainProbe {
        private boolean inBounds;
        private int terrainY;
        private boolean ocean;
        private boolean rawSurfaceWater;
        private boolean surfaceWaterDataAvailable;

        void set(boolean inBounds, int terrainY, boolean ocean, boolean rawSurfaceWater, boolean surfaceWaterDataAvailable) {
            this.inBounds = inBounds;
            this.terrainY = terrainY;
            this.ocean = ocean;
            this.rawSurfaceWater = rawSurfaceWater;
            this.surfaceWaterDataAvailable = surfaceWaterDataAvailable;
        }

        boolean inBounds() {
            return inBounds;
        }

        int terrainY() {
            return terrainY;
        }

        boolean ocean() {
            return ocean;
        }

        boolean rawSurfaceWater() {
            return rawSurfaceWater;
        }

        boolean surfaceWaterDataAvailable() {
            return surfaceWaterDataAvailable;
        }

        EarthSamplingResult.TerrainProbe toProbe() {
            return new EarthSamplingResult.TerrainProbe(
                inBounds,
                terrainY,
                ocean,
                rawSurfaceWater,
                surfaceWaterDataAvailable
            );
        }
    }

    public static final class LocalTileCaches {
        private final Map<TileKey, TerrariumTile> primaryTerrainTiles;
        private final Map<SourceTileKey, TerrariumTile> sourceTerrainTiles;
        private final Map<TileKey, EcoregionTile> ecoregionTiles;
        private final Map<TileKey, SurfaceWaterTileLookup> surfaceWaterTiles;
        private final OceanBathymetryRecovery.RecoverySampleCache recoverySampleCache;

        private final TileProjection.MutablePoint terrainPoint = new TileProjection.MutablePoint();
        private final TileProjection.MutablePoint ecoregionPoint = new TileProjection.MutablePoint();
        private final TileProjection.MutablePoint surfaceWaterPoint = new TileProjection.MutablePoint();

        private LocalTileCaches(int maxEntriesPerLayer) {
            this.primaryTerrainTiles = newLruTileMap(maxEntriesPerLayer);
            this.sourceTerrainTiles = newLruTileMap(maxEntriesPerLayer);
            this.ecoregionTiles = newLruTileMap(maxEntriesPerLayer);
            this.surfaceWaterTiles = newLruTileMap(maxEntriesPerLayer);
            this.recoverySampleCache = new OceanBathymetryRecovery.RecoverySampleCache(maxEntriesPerLayer * 16);
        }

        public static LocalTileCaches hotPathCaches() {
            return hotPathCaches(TerrariumRuntimeConfig.DEFAULT_SAMPLING_CONFIG.biomeLocalCacheEntries());
        }

        public static LocalTileCaches hotPathCaches(int maxEntriesPerLayer) {
            return new LocalTileCaches(maxEntriesPerLayer);
        }

        public void clear() {
            primaryTerrainTiles.clear();
            sourceTerrainTiles.clear();
            ecoregionTiles.clear();
            surfaceWaterTiles.clear();
            recoverySampleCache.clear();
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

        void resetTransientRecoveryCache() {
            recoverySampleCache.clear();
        }

        OceanBathymetryRecovery.RecoverySampleCache recoverySampleCache() {
            return recoverySampleCache;
        }

        int totalEntries() {
            return primaryTerrainTiles.size()
                + sourceTerrainTiles.size()
                + ecoregionTiles.size()
                + surfaceWaterTiles.size()
                + recoverySampleCache.size();
        }

        private static <K, V> Map<K, V> newLruTileMap(int maxEntries) {
            int boundedMaxEntries = Math.max(1, maxEntries);
            return new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > boundedMaxEntries;
                }
            };
        }
    }

    private record SourceTileKey(int sourceZoom, TileKey tileKey) {
    }

    private static final class ChunkCacheState {
        private LocalTileCaches caches =
            new LocalTileCaches(TerrariumRuntimeConfig.DEFAULT_SAMPLING_CONFIG.chunkLocalCacheEntries());
        private int maxEntriesPerLayer = TerrariumRuntimeConfig.DEFAULT_SAMPLING_CONFIG.chunkLocalCacheEntries();
        private long runtimeGeneration = Long.MIN_VALUE;
        private long lastAccessNanos = Long.MIN_VALUE;

        private LocalTileCaches caches() {
            return caches;
        }

        private int maxEntriesPerLayer() {
            return maxEntriesPerLayer;
        }

        private void recreateCaches(int maxEntriesPerLayer) {
            this.maxEntriesPerLayer = Math.max(1, maxEntriesPerLayer);
            this.caches = new LocalTileCaches(this.maxEntriesPerLayer);
        }

        private long runtimeGeneration() {
            return runtimeGeneration;
        }

        private void setRuntimeGeneration(long runtimeGeneration) {
            this.runtimeGeneration = runtimeGeneration;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }

        private void setLastAccessNanos(long lastAccessNanos) {
            this.lastAccessNanos = lastAccessNanos;
        }
    }

    private static boolean isIdleExpired(long lastAccessNanos, int idleSeconds, long nowNanos) {
        if (idleSeconds <= 0 || lastAccessNanos == Long.MIN_VALUE) {
            return false;
        }
        long idleNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(idleSeconds);
        return nowNanos - lastAccessNanos >= idleNanos;
    }
}
