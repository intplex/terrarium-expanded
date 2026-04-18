package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerrainService {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    static final int CHUNK_WIDTH = 16;
    static final int OUT_OF_BOUNDS_SOLID_TOP_Y = EarthGenConfig.MIN_Y - 1;

    private TerrainService() {
    }

    public static double continentalnessAtXZ(DensityFunction.FunctionContext functionContext) {
        return snapshotFor(functionContext.blockX(), functionContext.blockZ()).resolveContinentalness(functionContext.blockX(), functionContext.blockZ());
    }

    public static double depthDensityAtY(DensityFunction.FunctionContext functionContext) {
        return snapshotFor(functionContext.blockX(), functionContext.blockZ()).resolveDepth(functionContext.blockX(), functionContext.blockZ());
    }

    public static double envelopeDensityAtXYZ(DensityFunction.FunctionContext functionContext) {
        int solidTopY = snapshotFor(functionContext.blockX(), functionContext.blockZ())
            .resolveEffectiveSolidTopY(functionContext.blockX(), functionContext.blockZ());
        return functionContext.blockY() <= solidTopY ? 1.0 : -1.0;
    }

    public static double envelopDensityAtXYZ(DensityFunction.FunctionContext functionContext) {
        return envelopeDensityAtXYZ(functionContext);
    }

    public static int terrainYAtXZ(int blockX, int blockZ) {
        int resolvedY = snapshotFor(blockX, blockZ).resolveRawTerrainY(blockX, blockZ);
        return resolvedY == OUT_OF_BOUNDS_SOLID_TOP_Y ? EarthGenConfig.MIN_TERRAIN_Y : resolvedY;
    }

    public static int effectiveSolidTopYAtXZ(int blockX, int blockZ) {
        int resolvedY = snapshotFor(blockX, blockZ).resolveEffectiveSolidTopY(blockX, blockZ);
        return resolvedY == OUT_OF_BOUNDS_SOLID_TOP_Y ? EarthGenConfig.MIN_TERRAIN_Y : resolvedY;
    }

    public static int inlandWaterSurfaceYAtXZ(int blockX, int blockZ) {
        return snapshotFor(blockX, blockZ).resolveWaterSurfaceY(blockX, blockZ);
    }

    public static WaterBodyKind inlandWaterKindAtXZ(int blockX, int blockZ) {
        return snapshotFor(blockX, blockZ).resolveWaterKind(blockX, blockZ);
    }

    public static boolean inlandWaterEnabled() {
        return TerrainServices.requireContext().terrainRuntimeState().inlandWaterSettings().enabled();
    }

    public static double erosionAtXZ(DensityFunction.FunctionContext functionContext) {
        return snapshotFor(functionContext.blockX(), functionContext.blockZ()).resolveErosion(functionContext.blockX(), functionContext.blockZ());
    }

    public static double weirdnessAtXZ(DensityFunction.FunctionContext functionContext) {
        return snapshotFor(functionContext.blockX(), functionContext.blockZ()).resolveWeirdness(functionContext.blockX(), functionContext.blockZ());
    }

    public static void clearCaches() {
        EarthRuntimeContext context = TerrainServices.requireContext();
        context.clearCaches();
        WorldgenPlayerDiagnostics.clear();
    }

    static double depthFromTerrainY(int terrainY) {
        if (terrainY >= EarthGenConfig.SEA_LEVEL) {
            return clamp((terrainY - EarthGenConfig.SEA_LEVEL) / (double) Math.max(1, EarthGenConfig.MAX_TERRAIN_Y - EarthGenConfig.SEA_LEVEL));
        }
        return clamp((terrainY - EarthGenConfig.SEA_LEVEL) / (double) Math.max(1, EarthGenConfig.SEA_LEVEL - EarthGenConfig.MIN_TERRAIN_Y));
    }

    static int chunkCacheEntryCapacity(TerrariumRuntimeConfig runtimeConfig) {
        return runtimeConfig.terrainChunkCacheEntries();
    }

    static RuntimeState newRuntimeState(TerrariumRuntimeConfig runtimeConfig) {
        return new RuntimeState(
            InlandWaterSettings.loadFromRuntimeConfig(runtimeConfig),
            SurfaceWaterCoverageSettings.DEFAULT,
            new BoundedDedupeSet<>(8192),
            new BoundedDedupeSet<>(8192),
            new BoundedDedupeSet<>(8192),
            new BoundedDedupeSet<>(8192),
            ThreadLocal.withInitial(HotSnapshotCache::new),
            new ChunkSnapshotCache(chunkCacheEntryCapacity(runtimeConfig))
        );
    }

    static String sampleContextLabel(TileKey tileKey, int blockX, int blockZ) {
        return "tile=" + tileKey
            + " blockX=" + blockX
            + " blockZ=" + blockZ
            + " chunkX=" + Math.floorDiv(blockX, CHUNK_WIDTH)
            + " chunkZ=" + Math.floorDiv(blockZ, CHUNK_WIDTH)
            + " geo=" + geoDebugString(blockX, blockZ)
            + " " + WorldgenPlayerDiagnostics.currentPlayerLabel();
    }

    static void logWarn(String message, Object arg1, Object arg2) {
        LOGGER.warn(message, arg1, arg2);
    }

    static void logWarn(String message, Object arg1, Object arg2, Object arg3) {
        LOGGER.warn(message, arg1, arg2, arg3);
    }

    private static TerrainChunkSnapshot snapshotFor(int blockX, int blockZ) {
        EarthRuntimeContext context = TerrainServices.requireContext();
        RuntimeState runtimeState = context.terrainRuntimeState();
        HotSnapshotCache hotCache = runtimeState.hotSnapshots().get();
        TerrainChunkSnapshot hot = hotCache.get(blockX, blockZ);
        if (hot != null) {
            return hot;
        }
        TerrainChunkSnapshot loaded = runtimeState.snapshotCache().getOrBuildFor(
            blockX,
            blockZ,
            (chunkMinX, chunkMinZ) -> TerrainChunkSnapshotBuilder.build(chunkMinX, chunkMinZ, runtimeState, context)
        );
        hotCache.put(loaded);
        return loaded;
    }

    private static String geoDebugString(int blockX, int blockZ) {
        return EarthGenConfig.blockToGeo(blockX, blockZ)
            .map(geo -> String.format(Locale.ROOT, "lat=%.5f lon=%.5f", geo.latitude(), geo.longitude()))
            .orElse("out-of-bounds");
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    record ColumnMetrics(float continentalness, float erosion, float weirdness, float depth) {
    }

    static final class RuntimeState {
        private final InlandWaterSettings inlandWaterSettings;
        private final SurfaceWaterCoverageSettings surfaceWaterCoverageSettings;
        private final BoundedDedupeSet<TileKey> knownMissingSurfaceWaterTiles;
        private final BoundedDedupeSet<TileKey> loggedFailedSurfaceWaterTiles;
        private final BoundedDedupeSet<TileKey> loggedTerrainTileFailures;
        private final BoundedDedupeSet<TileKey> loggedEcoregionTileFailures;
        private final ThreadLocal<HotSnapshotCache> hotSnapshots;
        private final ChunkSnapshotCache snapshotCache;

        RuntimeState(
            InlandWaterSettings inlandWaterSettings,
            SurfaceWaterCoverageSettings surfaceWaterCoverageSettings,
            BoundedDedupeSet<TileKey> knownMissingSurfaceWaterTiles,
            BoundedDedupeSet<TileKey> loggedFailedSurfaceWaterTiles,
            BoundedDedupeSet<TileKey> loggedTerrainTileFailures,
            BoundedDedupeSet<TileKey> loggedEcoregionTileFailures,
            ThreadLocal<HotSnapshotCache> hotSnapshots,
            ChunkSnapshotCache snapshotCache
        ) {
            this.inlandWaterSettings = inlandWaterSettings;
            this.surfaceWaterCoverageSettings = surfaceWaterCoverageSettings;
            this.knownMissingSurfaceWaterTiles = knownMissingSurfaceWaterTiles;
            this.loggedFailedSurfaceWaterTiles = loggedFailedSurfaceWaterTiles;
            this.loggedTerrainTileFailures = loggedTerrainTileFailures;
            this.loggedEcoregionTileFailures = loggedEcoregionTileFailures;
            this.hotSnapshots = hotSnapshots;
            this.snapshotCache = snapshotCache;
        }

        InlandWaterSettings inlandWaterSettings() {
            return inlandWaterSettings;
        }

        SurfaceWaterCoverageSettings surfaceWaterCoverageSettings() {
            return surfaceWaterCoverageSettings;
        }

        BoundedDedupeSet<TileKey> knownMissingSurfaceWaterTiles() {
            return knownMissingSurfaceWaterTiles;
        }

        BoundedDedupeSet<TileKey> loggedFailedSurfaceWaterTiles() {
            return loggedFailedSurfaceWaterTiles;
        }

        BoundedDedupeSet<TileKey> loggedTerrainTileFailures() {
            return loggedTerrainTileFailures;
        }

        BoundedDedupeSet<TileKey> loggedEcoregionTileFailures() {
            return loggedEcoregionTileFailures;
        }

        ThreadLocal<HotSnapshotCache> hotSnapshots() {
            return hotSnapshots;
        }

        ChunkSnapshotCache snapshotCache() {
            return snapshotCache;
        }

        int snapshotCacheMaxEntries() {
            return snapshotCache.maxEntries();
        }

        void clear() {
            hotSnapshots.remove();
            snapshotCache.clear();
            knownMissingSurfaceWaterTiles.clear();
            loggedFailedSurfaceWaterTiles.clear();
            loggedTerrainTileFailures.clear();
            loggedEcoregionTileFailures.clear();
        }
    }

    private record ChunkKey(int chunkMinX, int chunkMinZ) {
    }

    private static final class HotSnapshotCache {
        private TerrainChunkSnapshot latest;
        private TerrainChunkSnapshot previous;

        private TerrainChunkSnapshot get(int blockX, int blockZ) {
            if (latest != null && latest.containsBlock(blockX, blockZ)) {
                return latest;
            }
            if (previous != null && previous.containsBlock(blockX, blockZ)) {
                TerrainChunkSnapshot promote = previous;
                previous = latest;
                latest = promote;
                return promote;
            }
            return null;
        }

        private void put(TerrainChunkSnapshot snapshot) {
            if (latest != snapshot) {
                previous = latest;
                latest = snapshot;
            }
        }
    }

    private static final class ChunkSnapshotCache {
        private final int maxEntries;
        private final Map<ChunkKey, TerrainChunkSnapshot> entries;
        private final ConcurrentHashMap<ChunkKey, ReentrantLock> inFlightLocks;

        private ChunkSnapshotCache(int maxEntries) {
            this.maxEntries = maxEntries;
            this.inFlightLocks = new ConcurrentHashMap<>();
            this.entries = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkKey, TerrainChunkSnapshot> eldest) {
                    return size() > ChunkSnapshotCache.this.maxEntries;
                }
            };
        }

        TerrainChunkSnapshot getOrBuildFor(int blockX, int blockZ, ChunkSnapshotBuilder builder) {
            ChunkKey key = new ChunkKey(chunkMinForBlock(blockX), chunkMinForBlock(blockZ));
            ReentrantLock lock;
            synchronized (this) {
                TerrainChunkSnapshot context = entries.get(key);
                if (context != null) {
                    return context;
                }
                lock = inFlightLocks.computeIfAbsent(key, ignored -> new ReentrantLock());
            }

            lock.lock();
            try {
                synchronized (this) {
                    TerrainChunkSnapshot context = entries.get(key);
                    if (context != null) {
                        return context;
                    }
                }
                TerrainChunkSnapshot loaded = builder.build(key.chunkMinX(), key.chunkMinZ());
                synchronized (this) {
                    TerrainChunkSnapshot existing = entries.get(key);
                    if (existing != null) {
                        return existing;
                    }
                    entries.put(key, loaded);
                    return loaded;
                }
            } finally {
                lock.unlock();
                inFlightLocks.remove(key, lock);
            }
        }

        synchronized void clear() {
            entries.clear();
            inFlightLocks.clear();
        }

        int maxEntries() {
            return maxEntries;
        }

        private static int chunkMinForBlock(int blockCoord) {
            return Math.floorDiv(blockCoord, CHUNK_WIDTH) * CHUNK_WIDTH;
        }
    }

    @FunctionalInterface
    private interface ChunkSnapshotBuilder {
        TerrainChunkSnapshot build(int chunkMinX, int chunkMinZ);
    }
}
