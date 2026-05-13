package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OceanBathymetryRecovery {
    public static final int RECOVERY_MIN_WORLD_ZOOM = 11;
    public static final int SOURCE_ZOOM = 10;
    public static final int FALLBACK_SOURCE_ZOOM = 8;
    private static final int[] SOURCE_ZOOM_CHAIN = new int[] { SOURCE_ZOOM, FALLBACK_SOURCE_ZOOM };
    public static final int ECOREGION_NO_DATA_COLOR = 0x000000;
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final long POOR_GEN_EVALUATION_INTERVAL_ATTEMPTS = 8192L;
    private static final long POOR_GEN_MIN_ATTEMPTS = 8192L;
    private static final double POOR_GEN_MAX_APPLY_RATE = 0.02;
    private static final double POOR_GEN_MIN_TILE_FAILURE_RATE = 0.50;
    private static final long RECOVERY_DIAGNOSTICS_INTERVAL_ATTEMPTS = 65_536L;
    private static final Object DIAGNOSTICS_LOCK = new Object();
    private static final LongAdder recoverySamplingNanos = new LongAdder();
    private static final LongAdder sourceZoom10Attempted = new LongAdder();
    private static final LongAdder sourceZoom10Useful = new LongAdder();
    private static final LongAdder sourceZoom10Zero = new LongAdder();
    private static final LongAdder sourceZoom10Unavailable = new LongAdder();
    private static final LongAdder sourceZoom10ZeroNeighborhoodSkipped = new LongAdder();
    private static final LongAdder sourceZoom8Attempted = new LongAdder();
    private static final LongAdder sourceZoom8Useful = new LongAdder();
    private static final LongAdder sourceZoom8Zero = new LongAdder();
    private static final LongAdder sourceZoom8Unavailable = new LongAdder();
    private static final LongAdder sourceZoom8ZeroNeighborhoodSkipped = new LongAdder();
    private static final LongAdder recoveryMemoHits = new LongAdder();
    private static boolean modeLogged;
    private static long lastPoorGenWarningWindow = -1L;
    private static long lastRecoveryDiagnosticsWindow = -1L;
    private static long recoveryAttempted;
    private static long recoveryApplied;
    private static long gateFailedEcoregion;
    private static long gateFailedWater;
    private static long gateFailedTile;

    private OceanBathymetryRecovery() {
    }

    public static boolean isRecoveryActiveForZoom(int worldZoom) {
        return worldZoom >= RECOVERY_MIN_WORLD_ZOOM;
    }

    public static boolean shouldAttemptRecovery(int worldZoom, boolean terrainSampleAvailable, double meters) {
        return isRecoveryActiveForZoom(worldZoom) && terrainSampleAvailable && meters == 0.0;
    }

    public static boolean isEcoregionNoDataColor(int colorRgb) {
        return colorRgb == ECOREGION_NO_DATA_COLOR;
    }

    public static int[] sourceZoomChain() {
        return SOURCE_ZOOM_CHAIN.clone();
    }

    public static void logModeIfActive(int worldZoom) {
        if (!isRecoveryActiveForZoom(worldZoom)) {
            return;
        }
        boolean shouldLog;
        synchronized (DIAGNOSTICS_LOCK) {
            shouldLog = !modeLogged;
            modeLogged = true;
        }
        if (shouldLog) {
            LOGGER.info(
                "[TX-BATHY] ocean recovery active mode=zoom>=11 source_zooms={} interpolation=bilinear gating=meters==0&&ecoregion==#000000&&surface_water==true",
                Arrays.toString(SOURCE_ZOOM_CHAIN)
            );
        }
    }

    public static void recordRecoveryAttempted() {
        synchronized (DIAGNOSTICS_LOCK) {
            recoveryAttempted++;
            if (recoveryAttempted % POOR_GEN_EVALUATION_INTERVAL_ATTEMPTS == 0L) {
                maybeLogPoorTerrainGenerationLocked(recoveryAttempted);
            }
            if (recoveryAttempted % RECOVERY_DIAGNOSTICS_INTERVAL_ATTEMPTS == 0L) {
                maybeLogRecoveryDiagnosticsLocked(recoveryAttempted);
            }
        }
    }

    public static void recordRecoveryApplied() {
        synchronized (DIAGNOSTICS_LOCK) {
            recoveryApplied++;
        }
    }

    public static void recordGateFailedEcoregion() {
        synchronized (DIAGNOSTICS_LOCK) {
            gateFailedEcoregion++;
        }
    }

    public static void recordGateFailedWater() {
        synchronized (DIAGNOSTICS_LOCK) {
            gateFailedWater++;
        }
    }

    public static void recordGateFailedTile() {
        synchronized (DIAGNOSTICS_LOCK) {
            gateFailedTile++;
        }
    }

    static void resetDiagnosticsForTesting() {
        synchronized (DIAGNOSTICS_LOCK) {
            modeLogged = false;
            lastPoorGenWarningWindow = -1L;
            lastRecoveryDiagnosticsWindow = -1L;
            recoveryAttempted = 0L;
            recoveryApplied = 0L;
            gateFailedEcoregion = 0L;
            gateFailedWater = 0L;
            gateFailedTile = 0L;
        }
        recoverySamplingNanos.reset();
        sourceZoom10Attempted.reset();
        sourceZoom10Useful.reset();
        sourceZoom10Zero.reset();
        sourceZoom10Unavailable.reset();
        sourceZoom10ZeroNeighborhoodSkipped.reset();
        sourceZoom8Attempted.reset();
        sourceZoom8Useful.reset();
        sourceZoom8Zero.reset();
        sourceZoom8Unavailable.reset();
        sourceZoom8ZeroNeighborhoodSkipped.reset();
        recoveryMemoHits.reset();
    }

    static DiagnosticsSnapshot diagnosticsSnapshotForTesting() {
        synchronized (DIAGNOSTICS_LOCK) {
            return new DiagnosticsSnapshot(
                recoveryAttempted,
                recoveryApplied,
                gateFailedEcoregion,
                gateFailedWater,
                gateFailedTile,
                modeLogged,
                lastPoorGenWarningWindow,
                lastRecoveryDiagnosticsWindow,
                recoverySamplingNanos.sum(),
                sourceZoom10Attempted.sum(),
                sourceZoom10Useful.sum(),
                sourceZoom10Zero.sum(),
                sourceZoom10Unavailable.sum(),
                sourceZoom10ZeroNeighborhoodSkipped.sum(),
                sourceZoom8Attempted.sum(),
                sourceZoom8Useful.sum(),
                sourceZoom8Zero.sum(),
                sourceZoom8Unavailable.sum(),
                sourceZoom8ZeroNeighborhoodSkipped.sum(),
                recoveryMemoHits.sum()
            );
        }
    }

    public static OptionalDouble sampleBilinearMeters(
        int blockX,
        int blockZ,
        int worldZoom,
        int sourceZoom,
        ZoomMetersSampler sampler,
        InterpolationClamp clamp
    ) {
        return sampleBilinearMeters(blockX, blockZ, worldZoom, sourceZoom, sampler, clamp, null);
    }

    private static OptionalDouble sampleBilinearMeters(
        int blockX,
        int blockZ,
        int worldZoom,
        int sourceZoom,
        ZoomMetersSampler sampler,
        InterpolationClamp clamp,
        RecoverySampleCache cache
    ) {
        if (sourceZoom > worldZoom) {
            return OptionalDouble.empty();
        }
        Optional<BilinearGeometry> geometry = bilinearGeometry(blockX, blockZ, worldZoom, sourceZoom);
        if (geometry.isEmpty()) {
            return OptionalDouble.empty();
        }

        BilinearGeometry sampleGeometry = geometry.get();
        NeighborhoodSamples samples = sampleNeighborhoodMeters(sampleGeometry.key(), sampler, cache);
        if (samples == null) {
            return OptionalDouble.empty();
        }

        double top = lerp(samples.topLeft(), samples.topRight(), sampleGeometry.fracX());
        double bottom = lerp(samples.bottomLeft(), samples.bottomRight(), sampleGeometry.fracX());
        double interpolated = lerp(top, bottom, sampleGeometry.fracY());
        if (clamp == InterpolationClamp.OCEAN_ONLY) {
            return OptionalDouble.of(Math.min(0.0, interpolated));
        }
        return OptionalDouble.of(interpolated);
    }

    public static OptionalDouble sampleZoom10BilinearMeters(int blockX, int blockZ, int worldZoom, Zoom10MetersSampler sampler) {
        return sampleBilinearMeters(
            blockX,
            blockZ,
            worldZoom,
            SOURCE_ZOOM,
            sampler,
            InterpolationClamp.OCEAN_ONLY
        );
    }

    public static OptionalDouble sampleRecoveryChainMeters(
        int blockX,
        int blockZ,
        int worldZoom,
        SourceZoomMetersSampler sampler
    ) {
        return sampleRecoveryChainMeters(blockX, blockZ, worldZoom, sampler, null);
    }

    public static OptionalDouble sampleRecoveryChainMeters(
        int blockX,
        int blockZ,
        int worldZoom,
        SourceZoomMetersSampler sampler,
        RecoverySampleCache cache
    ) {
        long startedNanos = System.nanoTime();
        try {
            return sampleRecoveryChainMetersInternal(blockX, blockZ, worldZoom, sampler, cache);
        } finally {
            recoverySamplingNanos.add(System.nanoTime() - startedNanos);
        }
    }

    private static OptionalDouble sampleRecoveryChainMetersInternal(
        int blockX,
        int blockZ,
        int worldZoom,
        SourceZoomMetersSampler sampler,
        RecoverySampleCache cache
    ) {
        for (int sourceZoom : SOURCE_ZOOM_CHAIN) {
            Optional<BilinearGeometry> geometry = bilinearGeometry(blockX, blockZ, worldZoom, sourceZoom);
            if (geometry.isEmpty()) {
                recordRecoverySourceUnavailable(sourceZoom);
                continue;
            }
            if (cache != null && cache.hasZeroNeighborhood(geometry.get().key())) {
                recordRecoverySourceZeroNeighborhoodSkipped(sourceZoom);
                continue;
            }
            recordRecoverySourceAttempted(sourceZoom);
            OptionalDouble recovered = sampleBilinearMeters(
                blockX,
                blockZ,
                worldZoom,
                sourceZoom,
                (tileKey, localX, localY) -> sampler.sampleMeters(sourceZoom, tileKey, localX, localY),
                InterpolationClamp.OCEAN_ONLY,
                cache
            );
            if (recovered.isPresent() && recovered.getAsDouble() < 0.0) {
                recordRecoverySourceUseful(sourceZoom);
                return recovered;
            }
            if (recovered.isPresent()) {
                recordRecoverySourceZero(sourceZoom);
            } else {
                recordRecoverySourceUnavailable(sourceZoom);
            }
        }
        return OptionalDouble.empty();
    }

    private static Optional<BilinearGeometry> bilinearGeometry(int blockX, int blockZ, int worldZoom, int sourceZoom) {
        if (sourceZoom > worldZoom) {
            return Optional.empty();
        }
        int validatedSourceZoom = EarthGenConfig.validateZoom(sourceZoom);

        Optional<EarthGenConfig.TileSamplePoint> highZoomSample = EarthGenConfig.projectBlockToTerrainTile(blockX, blockZ, worldZoom);
        if (highZoomSample.isEmpty()) {
            return Optional.empty();
        }

        EarthGenConfig.TileSamplePoint samplePoint = highZoomSample.get();
        long highZoomGlobalX = (long) samplePoint.tileKey().x() * EarthGenConfig.TILE_SIZE + samplePoint.pixelX();
        long highZoomGlobalY = (long) samplePoint.tileKey().y() * EarthGenConfig.TILE_SIZE + samplePoint.pixelY();
        int scaleShift = worldZoom - validatedSourceZoom;
        double scale = Math.scalb(1.0, scaleShift);
        double sourceX = highZoomGlobalX / scale;
        double sourceY = highZoomGlobalY / scale;

        int maxSourceGlobal = EarthGenConfig.blockSpanForZoom(validatedSourceZoom) - 1;
        int x0 = clampToSourceGlobal((int) Math.floor(sourceX), maxSourceGlobal);
        int y0 = clampToSourceGlobal((int) Math.floor(sourceY), maxSourceGlobal);
        int x1 = Math.min(maxSourceGlobal, x0 + 1);
        int y1 = Math.min(maxSourceGlobal, y0 + 1);
        double fracX = sourceX - x0;
        double fracY = sourceY - y0;
        return Optional.of(new BilinearGeometry(new NeighborhoodKey(validatedSourceZoom, x0, y0, x1, y1), fracX, fracY));
    }

    private static NeighborhoodSamples sampleNeighborhoodMeters(
        NeighborhoodKey key,
        ZoomMetersSampler sampler,
        RecoverySampleCache cache
    ) {
        if (cache != null) {
            NeighborhoodSamples cached = cache.get(key);
            if (cached != null) {
                recoveryMemoHits.increment();
                return cached;
            }
        }

        OptionalDouble topLeft = sampleSourceGlobalPixelMeters(key.x0(), key.y0(), sampler);
        if (topLeft.isEmpty()) {
            return null;
        }
        OptionalDouble topRight = sampleSourceGlobalPixelMeters(key.x1(), key.y0(), sampler);
        if (topRight.isEmpty()) {
            return null;
        }
        OptionalDouble bottomLeft = sampleSourceGlobalPixelMeters(key.x0(), key.y1(), sampler);
        if (bottomLeft.isEmpty()) {
            return null;
        }
        OptionalDouble bottomRight = sampleSourceGlobalPixelMeters(key.x1(), key.y1(), sampler);
        if (bottomRight.isEmpty()) {
            return null;
        }

        NeighborhoodSamples samples = new NeighborhoodSamples(
            topLeft.getAsDouble(),
            topRight.getAsDouble(),
            bottomLeft.getAsDouble(),
            bottomRight.getAsDouble()
        );
        if (cache != null) {
            cache.put(key, samples);
        }
        return samples;
    }

    private static OptionalDouble sampleSourceGlobalPixelMeters(int sourceGlobalX, int sourceGlobalY, ZoomMetersSampler sampler) {
        int tileX = Math.floorDiv(sourceGlobalX, EarthGenConfig.TILE_SIZE);
        int tileY = Math.floorDiv(sourceGlobalY, EarthGenConfig.TILE_SIZE);
        int localX = Math.floorMod(sourceGlobalX, EarthGenConfig.TILE_SIZE);
        int localY = Math.floorMod(sourceGlobalY, EarthGenConfig.TILE_SIZE);
        Double meters = sampler.sampleMeters(new TileKey(tileX, tileY), localX, localY);
        if (meters == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(meters);
    }

    private static int clampToSourceGlobal(int globalPixel, int maxSourceGlobal) {
        if (globalPixel < 0) {
            return 0;
        }
        return Math.min(globalPixel, maxSourceGlobal);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void maybeLogPoorTerrainGenerationLocked(long attempts) {
        if (attempts < POOR_GEN_MIN_ATTEMPTS) {
            return;
        }

        long applied = recoveryApplied;
        long failedTile = gateFailedTile;
        double applyRate = attempts == 0L ? 0.0 : applied / (double) attempts;
        double tileFailureRate = attempts == 0L ? 0.0 : failedTile / (double) attempts;
        if (applyRate > POOR_GEN_MAX_APPLY_RATE || tileFailureRate < POOR_GEN_MIN_TILE_FAILURE_RATE) {
            return;
        }

        long window = attempts / POOR_GEN_EVALUATION_INTERVAL_ATTEMPTS;
        if (window <= lastPoorGenWarningWindow) {
            return;
        }
        lastPoorGenWarningWindow = window;

        LOGGER.warn(
            "[TX-BATHY] low recovery quality detected: recovery_attempted={} recovery_applied={} gate_failed_ecoregion={} gate_failed_water={} gate_failed_tile={} apply_rate={} tile_failure_rate={}",
            attempts,
            applied,
            gateFailedEcoregion,
            gateFailedWater,
            failedTile,
            String.format(Locale.ROOT, "%.4f", applyRate),
            String.format(Locale.ROOT, "%.4f", tileFailureRate)
        );
    }

    private static void maybeLogRecoveryDiagnosticsLocked(long attempts) {
        long window = attempts / RECOVERY_DIAGNOSTICS_INTERVAL_ATTEMPTS;
        if (window <= lastRecoveryDiagnosticsWindow) {
            return;
        }
        lastRecoveryDiagnosticsWindow = window;

        long elapsedNanos = recoverySamplingNanos.sum();
        double averageMicros = attempts == 0L ? 0.0 : elapsedNanos / 1_000.0 / attempts;
        LOGGER.info(
            "[TX-BATHY] recovery diagnostics attempts={} applied={} avg_us={} z10(attempted={}, useful={}, zero={}, unavailable={}, zero_skipped={}) z8(attempted={}, useful={}, zero={}, unavailable={}, zero_skipped={}) memo_hits={}",
            attempts,
            recoveryApplied,
            String.format(Locale.ROOT, "%.3f", averageMicros),
            sourceZoom10Attempted.sum(),
            sourceZoom10Useful.sum(),
            sourceZoom10Zero.sum(),
            sourceZoom10Unavailable.sum(),
            sourceZoom10ZeroNeighborhoodSkipped.sum(),
            sourceZoom8Attempted.sum(),
            sourceZoom8Useful.sum(),
            sourceZoom8Zero.sum(),
            sourceZoom8Unavailable.sum(),
            sourceZoom8ZeroNeighborhoodSkipped.sum(),
            recoveryMemoHits.sum()
        );
    }

    private static void recordRecoverySourceAttempted(int sourceZoom) {
        if (sourceZoom == SOURCE_ZOOM) {
            sourceZoom10Attempted.increment();
        } else if (sourceZoom == FALLBACK_SOURCE_ZOOM) {
            sourceZoom8Attempted.increment();
        }
    }

    private static void recordRecoverySourceUseful(int sourceZoom) {
        if (sourceZoom == SOURCE_ZOOM) {
            sourceZoom10Useful.increment();
        } else if (sourceZoom == FALLBACK_SOURCE_ZOOM) {
            sourceZoom8Useful.increment();
        }
    }

    private static void recordRecoverySourceZero(int sourceZoom) {
        if (sourceZoom == SOURCE_ZOOM) {
            sourceZoom10Zero.increment();
        } else if (sourceZoom == FALLBACK_SOURCE_ZOOM) {
            sourceZoom8Zero.increment();
        }
    }

    private static void recordRecoverySourceUnavailable(int sourceZoom) {
        if (sourceZoom == SOURCE_ZOOM) {
            sourceZoom10Unavailable.increment();
        } else if (sourceZoom == FALLBACK_SOURCE_ZOOM) {
            sourceZoom8Unavailable.increment();
        }
    }

    private static void recordRecoverySourceZeroNeighborhoodSkipped(int sourceZoom) {
        if (sourceZoom == SOURCE_ZOOM) {
            sourceZoom10ZeroNeighborhoodSkipped.increment();
        } else if (sourceZoom == FALLBACK_SOURCE_ZOOM) {
            sourceZoom8ZeroNeighborhoodSkipped.increment();
        }
    }

    @FunctionalInterface
    public interface ZoomMetersSampler {
        Double sampleMeters(TileKey key, int localX, int localY);
    }

    public enum InterpolationClamp {
        OCEAN_ONLY,
        UNCLAMPED
    }

    @FunctionalInterface
    public interface Zoom10MetersSampler extends ZoomMetersSampler {
        @Override
        Double sampleMeters(TileKey key, int localX, int localY);
    }

    @FunctionalInterface
    public interface SourceZoomMetersSampler {
        Double sampleMeters(int sourceZoom, TileKey key, int localX, int localY);
    }

    static final class RecoverySampleCache {
        private final Map<NeighborhoodKey, NeighborhoodSamples> neighborhoods;

        RecoverySampleCache(int maxEntries) {
            this.neighborhoods = newLruMap(maxEntries);
        }

        void clear() {
            neighborhoods.clear();
        }

        int size() {
            return neighborhoods.size();
        }

        private NeighborhoodSamples get(NeighborhoodKey key) {
            return neighborhoods.get(key);
        }

        private void put(NeighborhoodKey key, NeighborhoodSamples samples) {
            neighborhoods.put(key, samples);
        }

        private boolean hasZeroNeighborhood(NeighborhoodKey key) {
            NeighborhoodSamples samples = neighborhoods.get(key);
            return samples != null && samples.allZero();
        }

        private static <K, V> Map<K, V> newLruMap(int maxEntries) {
            int boundedMaxEntries = Math.max(1, maxEntries);
            return new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > boundedMaxEntries;
                }
            };
        }
    }

    private record BilinearGeometry(NeighborhoodKey key, double fracX, double fracY) {
    }

    private record NeighborhoodKey(int sourceZoom, int x0, int y0, int x1, int y1) {
    }

    private record NeighborhoodSamples(double topLeft, double topRight, double bottomLeft, double bottomRight) {
        private boolean allZero() {
            return topLeft == 0.0 && topRight == 0.0 && bottomLeft == 0.0 && bottomRight == 0.0;
        }
    }

    record DiagnosticsSnapshot(
        long recoveryAttempted,
        long recoveryApplied,
        long gateFailedEcoregion,
        long gateFailedWater,
        long gateFailedTile,
        boolean modeLogged,
        long lastPoorGenWarningWindow,
        long lastRecoveryDiagnosticsWindow,
        long recoverySamplingNanos,
        long sourceZoom10Attempted,
        long sourceZoom10Useful,
        long sourceZoom10Zero,
        long sourceZoom10Unavailable,
        long sourceZoom10ZeroNeighborhoodSkipped,
        long sourceZoom8Attempted,
        long sourceZoom8Useful,
        long sourceZoom8Zero,
        long sourceZoom8Unavailable,
        long sourceZoom8ZeroNeighborhoodSkipped,
        long recoveryMemoHits
    ) {
    }
}
