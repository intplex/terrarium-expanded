package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OceanBathymetryRecovery {
    public static final int RECOVERY_MIN_WORLD_ZOOM = 11;
    public static final int SOURCE_ZOOM = 10;
    public static final int ECOREGION_NO_DATA_COLOR = 0x000000;
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final long POOR_GEN_EVALUATION_INTERVAL_ATTEMPTS = 8192L;
    private static final long POOR_GEN_MIN_ATTEMPTS = 8192L;
    private static final double POOR_GEN_MAX_APPLY_RATE = 0.02;
    private static final double POOR_GEN_MIN_TILE_FAILURE_RATE = 0.50;
    private static final Object DIAGNOSTICS_LOCK = new Object();
    private static boolean modeLogged;
    private static long lastPoorGenWarningWindow = -1L;
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
                "[TX-BATHY] ocean recovery active mode=zoom>=11 source_zoom={} interpolation=bilinear gating=meters==0&&ecoregion==#000000&&surface_water==true",
                SOURCE_ZOOM
            );
        }
    }

    public static void recordRecoveryAttempted() {
        synchronized (DIAGNOSTICS_LOCK) {
            recoveryAttempted++;
            if (recoveryAttempted % POOR_GEN_EVALUATION_INTERVAL_ATTEMPTS == 0L) {
                maybeLogPoorTerrainGenerationLocked(recoveryAttempted);
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
            recoveryAttempted = 0L;
            recoveryApplied = 0L;
            gateFailedEcoregion = 0L;
            gateFailedWater = 0L;
            gateFailedTile = 0L;
        }
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
                lastPoorGenWarningWindow
            );
        }
    }

    public static OptionalDouble sampleZoom10BilinearMeters(int blockX, int blockZ, int worldZoom, Zoom10MetersSampler sampler) {
        if (worldZoom < SOURCE_ZOOM) {
            return OptionalDouble.empty();
        }

        Optional<EarthGenConfig.TileSamplePoint> highZoomSample = EarthGenConfig.projectBlockToTerrainTile(blockX, blockZ, worldZoom);
        if (highZoomSample.isEmpty()) {
            return OptionalDouble.empty();
        }

        EarthGenConfig.TileSamplePoint samplePoint = highZoomSample.get();
        long highZoomGlobalX = (long) samplePoint.tileKey().x() * EarthGenConfig.TILE_SIZE + samplePoint.pixelX();
        long highZoomGlobalY = (long) samplePoint.tileKey().y() * EarthGenConfig.TILE_SIZE + samplePoint.pixelY();
        int scaleShift = worldZoom - SOURCE_ZOOM;
        double scale = Math.scalb(1.0, scaleShift);
        double sourceX = highZoomGlobalX / scale;
        double sourceY = highZoomGlobalY / scale;

        int maxSourceGlobal = EarthGenConfig.blockSpanForZoom(SOURCE_ZOOM) - 1;
        int x0 = clampToSourceGlobal((int) Math.floor(sourceX), maxSourceGlobal);
        int y0 = clampToSourceGlobal((int) Math.floor(sourceY), maxSourceGlobal);
        int x1 = Math.min(maxSourceGlobal, x0 + 1);
        int y1 = Math.min(maxSourceGlobal, y0 + 1);
        double fracX = sourceX - x0;
        double fracY = sourceY - y0;

        OptionalDouble topLeft = sampleSourceGlobalPixelMeters(x0, y0, sampler);
        if (topLeft.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble topRight = sampleSourceGlobalPixelMeters(x1, y0, sampler);
        if (topRight.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble bottomLeft = sampleSourceGlobalPixelMeters(x0, y1, sampler);
        if (bottomLeft.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble bottomRight = sampleSourceGlobalPixelMeters(x1, y1, sampler);
        if (bottomRight.isEmpty()) {
            return OptionalDouble.empty();
        }

        double top = lerp(topLeft.getAsDouble(), topRight.getAsDouble(), fracX);
        double bottom = lerp(bottomLeft.getAsDouble(), bottomRight.getAsDouble(), fracX);
        double interpolated = lerp(top, bottom, fracY);
        return OptionalDouble.of(Math.min(0.0, interpolated));
    }

    private static OptionalDouble sampleSourceGlobalPixelMeters(int sourceGlobalX, int sourceGlobalY, Zoom10MetersSampler sampler) {
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

    @FunctionalInterface
    public interface Zoom10MetersSampler {
        Double sampleMeters(TileKey key, int localX, int localY);
    }

    record DiagnosticsSnapshot(
        long recoveryAttempted,
        long recoveryApplied,
        long gateFailedEcoregion,
        long gateFailedWater,
        long gateFailedTile,
        boolean modeLogged,
        long lastPoorGenWarningWindow
    ) {
    }
}
