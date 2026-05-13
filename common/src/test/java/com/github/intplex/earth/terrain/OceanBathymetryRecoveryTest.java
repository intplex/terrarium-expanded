package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OceanBathymetryRecoveryTest {
    @AfterEach
    void tearDown() {
        OceanBathymetryRecovery.resetDiagnosticsForTesting();
    }

    @Test
    void eligibilityRequiresZoomElevenTerrainDataAndZeroMeters() {
        assertFalse(OceanBathymetryRecovery.shouldAttemptRecovery(10, true, 0.0));
        assertFalse(OceanBathymetryRecovery.shouldAttemptRecovery(11, false, 0.0));
        assertFalse(OceanBathymetryRecovery.shouldAttemptRecovery(11, true, -1.0));
        assertTrue(OceanBathymetryRecovery.shouldAttemptRecovery(11, true, 0.0));
        assertTrue(OceanBathymetryRecovery.shouldAttemptRecovery(12, true, 0.0));
    }

    @Test
    void noDataEcoregionGateRequiresExactBlack() {
        assertTrue(OceanBathymetryRecovery.isEcoregionNoDataColor(0x000000));
        assertFalse(OceanBathymetryRecovery.isEcoregionNoDataColor(0x000001));
        assertFalse(OceanBathymetryRecovery.isEcoregionNoDataColor(0x101010));
    }

    @Test
    void bilinearAtExactPixelMatchesSourceValue() {
        int zoom = 11;
        int blockX = blockFromGlobalPixel(200, zoom);
        int blockZ = blockFromGlobalPixel(400, zoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleZoom10BilinearMeters(
            blockX,
            blockZ,
            zoom,
            (tileKey, localX, localY) -> {
                int sourceGlobalX = tileKey.x() * EarthGenConfig.TILE_SIZE + localX;
                int sourceGlobalY = tileKey.y() * EarthGenConfig.TILE_SIZE + localY;
                return -(sourceGlobalX * 1000.0 + sourceGlobalY);
            }
        );

        assertTrue(recovered.isPresent());
        assertEquals(-100_200.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void bilinearInterpolatesFractionalCoordinates() {
        int zoom = 11;
        int blockX = blockFromGlobalPixel(21, zoom);
        int blockZ = blockFromGlobalPixel(41, zoom);
        Map<Long, Double> sourceMeters = new HashMap<>();
        sourceMeters.put(pixelKey(10, 20), -100.0);
        sourceMeters.put(pixelKey(11, 20), -200.0);
        sourceMeters.put(pixelKey(10, 21), -300.0);
        sourceMeters.put(pixelKey(11, 21), -500.0);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleZoom10BilinearMeters(
            blockX,
            blockZ,
            zoom,
            (tileKey, localX, localY) -> {
                int sourceGlobalX = tileKey.x() * EarthGenConfig.TILE_SIZE + localX;
                int sourceGlobalY = tileKey.y() * EarthGenConfig.TILE_SIZE + localY;
                return sourceMeters.get(pixelKey(sourceGlobalX, sourceGlobalY));
            }
        );

        assertTrue(recovered.isPresent());
        assertEquals(-275.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void bilinearSupportsArbitrarySourceZoomWithoutClamp() {
        int worldZoom = 9;
        int sourceZoom = 8;
        int blockX = blockFromGlobalPixel(200, worldZoom);
        int blockZ = blockFromGlobalPixel(400, worldZoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleBilinearMeters(
            blockX,
            blockZ,
            worldZoom,
            sourceZoom,
            (tileKey, localX, localY) -> {
                int sourceGlobalX = tileKey.x() * EarthGenConfig.TILE_SIZE + localX;
                int sourceGlobalY = tileKey.y() * EarthGenConfig.TILE_SIZE + localY;
                return sourceGlobalX * 10.0 + sourceGlobalY;
            },
            OceanBathymetryRecovery.InterpolationClamp.UNCLAMPED
        );

        assertTrue(recovered.isPresent());
        assertEquals(1200.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void bilinearCrossesTileBoundaries() {
        int zoom = 12;
        int blockX = blockFromGlobalPixel(1022, zoom);
        int blockZ = blockFromGlobalPixel(1021, zoom);
        Map<Long, Double> sourceMeters = new HashMap<>();
        sourceMeters.put(pixelKey(255, 255), -100.0);
        sourceMeters.put(pixelKey(256, 255), -200.0);
        sourceMeters.put(pixelKey(255, 256), -300.0);
        sourceMeters.put(pixelKey(256, 256), -400.0);
        Set<TileKey> sampledTiles = new HashSet<>();

        OptionalDouble recovered = OceanBathymetryRecovery.sampleZoom10BilinearMeters(
            blockX,
            blockZ,
            zoom,
            (tileKey, localX, localY) -> {
                sampledTiles.add(tileKey);
                int sourceGlobalX = tileKey.x() * EarthGenConfig.TILE_SIZE + localX;
                int sourceGlobalY = tileKey.y() * EarthGenConfig.TILE_SIZE + localY;
                return sourceMeters.get(pixelKey(sourceGlobalX, sourceGlobalY));
            }
        );

        assertTrue(recovered.isPresent());
        assertEquals(-200.0, recovered.getAsDouble(), 1e-9);
        assertTrue(sampledTiles.contains(new TileKey(0, 0)));
        assertTrue(sampledTiles.contains(new TileKey(1, 0)));
        assertTrue(sampledTiles.contains(new TileKey(0, 1)));
        assertTrue(sampledTiles.contains(new TileKey(1, 1)));
    }

    @Test
    void bilinearClampsRecoveredDepthToOcean() {
        int zoom = 11;
        int blockX = blockFromGlobalPixel(21, zoom);
        int blockZ = blockFromGlobalPixel(41, zoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleZoom10BilinearMeters(
            blockX,
            blockZ,
            zoom,
            (tileKey, localX, localY) -> 120.0
        );

        assertTrue(recovered.isPresent());
        assertEquals(0.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void recoveryChainFallsBackWhenPrimarySourceReturnsZero() {
        int zoom = 12;
        int blockX = blockFromGlobalPixel(84, zoom);
        int blockZ = blockFromGlobalPixel(164, zoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockX,
            blockZ,
            zoom,
            (sourceZoom, tileKey, localX, localY) -> sourceZoom == OceanBathymetryRecovery.SOURCE_ZOOM ? 0.0 : -800.0
        );

        assertTrue(recovered.isPresent());
        assertEquals(-800.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void recoveryChainIgnoresClampedZeroFromPositiveSources() {
        int zoom = 12;
        int blockX = blockFromGlobalPixel(84, zoom);
        int blockZ = blockFromGlobalPixel(164, zoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockX,
            blockZ,
            zoom,
            (sourceZoom, tileKey, localX, localY) -> 120.0
        );

        assertTrue(recovered.isEmpty());
    }

    @Test
    void recoveryChainMemoizesSourceNeighborhoodSamples() {
        int zoom = 12;
        OceanBathymetryRecovery.RecoverySampleCache cache = new OceanBathymetryRecovery.RecoverySampleCache(16);
        AtomicInteger sourceCalls = new AtomicInteger();

        OptionalDouble first = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockFromGlobalPixel(84, zoom),
            blockFromGlobalPixel(164, zoom),
            zoom,
            (sourceZoom, tileKey, localX, localY) -> {
                sourceCalls.incrementAndGet();
                return -100.0;
            },
            cache
        );
        OptionalDouble second = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockFromGlobalPixel(85, zoom),
            blockFromGlobalPixel(165, zoom),
            zoom,
            (sourceZoom, tileKey, localX, localY) -> {
                sourceCalls.incrementAndGet();
                return -100.0;
            },
            cache
        );

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(4, sourceCalls.get());
        assertTrue(OceanBathymetryRecovery.diagnosticsSnapshotForTesting().recoveryMemoHits() > 0);
    }

    @Test
    void recoveryChainSkipsMemoizedZeroPrimaryNeighborhood() {
        int zoom = 12;
        OceanBathymetryRecovery.RecoverySampleCache cache = new OceanBathymetryRecovery.RecoverySampleCache(16);
        AtomicInteger zoomTenCalls = new AtomicInteger();
        AtomicInteger zoomEightCalls = new AtomicInteger();

        OceanBathymetryRecovery.SourceZoomMetersSampler sampler = (sourceZoom, tileKey, localX, localY) -> {
            if (sourceZoom == OceanBathymetryRecovery.SOURCE_ZOOM) {
                zoomTenCalls.incrementAndGet();
                return 0.0;
            }
            zoomEightCalls.incrementAndGet();
            return -700.0;
        };

        OptionalDouble first = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockFromGlobalPixel(84, zoom),
            blockFromGlobalPixel(164, zoom),
            zoom,
            sampler,
            cache
        );
        OptionalDouble second = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockFromGlobalPixel(85, zoom),
            blockFromGlobalPixel(165, zoom),
            zoom,
            sampler,
            cache
        );

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(-700.0, first.getAsDouble(), 1e-9);
        assertEquals(-700.0, second.getAsDouble(), 1e-9);
        assertEquals(4, zoomTenCalls.get());
        assertEquals(4, zoomEightCalls.get());

        OceanBathymetryRecovery.DiagnosticsSnapshot snapshot = OceanBathymetryRecovery.diagnosticsSnapshotForTesting();
        assertEquals(1L, snapshot.sourceZoom10Zero());
        assertEquals(1L, snapshot.sourceZoom10ZeroNeighborhoodSkipped());
        assertEquals(2L, snapshot.sourceZoom8Useful());
    }

    @Test
    void unclampedInterpolationRetainsPositiveMeters() {
        int zoom = 11;
        int blockX = blockFromGlobalPixel(21, zoom);
        int blockZ = blockFromGlobalPixel(41, zoom);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleBilinearMeters(
            blockX,
            blockZ,
            zoom,
            10,
            (tileKey, localX, localY) -> 120.0,
            OceanBathymetryRecovery.InterpolationClamp.UNCLAMPED
        );

        assertTrue(recovered.isPresent());
        assertEquals(120.0, recovered.getAsDouble(), 1e-9);
    }

    @Test
    void bilinearAbortsWhenSourceSampleIsUnavailable() {
        int zoom = 11;
        int blockX = blockFromGlobalPixel(21, zoom);
        int blockZ = blockFromGlobalPixel(41, zoom);
        Map<Long, Double> sourceMeters = new HashMap<>();
        sourceMeters.put(pixelKey(10, 20), -100.0);
        sourceMeters.put(pixelKey(11, 20), -200.0);
        sourceMeters.put(pixelKey(10, 21), -300.0);

        OptionalDouble recovered = OceanBathymetryRecovery.sampleZoom10BilinearMeters(
            blockX,
            blockZ,
            zoom,
            (tileKey, localX, localY) -> {
                int sourceGlobalX = tileKey.x() * EarthGenConfig.TILE_SIZE + localX;
                int sourceGlobalY = tileKey.y() * EarthGenConfig.TILE_SIZE + localY;
                return sourceMeters.get(pixelKey(sourceGlobalX, sourceGlobalY));
            }
        );

        assertTrue(recovered.isEmpty());
    }

    @Test
    void diagnosticsCountersRemainConsistentUnderConcurrency() {
        OceanBathymetryRecovery.resetDiagnosticsForTesting();
        int threads = 8;
        int attemptsPerThread = 1024;
        int expectedAttempts = threads * attemptsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                futures[thread] = CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < attemptsPerThread; i++) {
                        OceanBathymetryRecovery.recordRecoveryAttempted();
                        OceanBathymetryRecovery.recordGateFailedTile();
                    }
                }, pool);
            }
            CompletableFuture.allOf(futures).join();
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        OceanBathymetryRecovery.DiagnosticsSnapshot snapshot = OceanBathymetryRecovery.diagnosticsSnapshotForTesting();
        assertEquals(expectedAttempts, snapshot.recoveryAttempted());
        assertEquals(expectedAttempts, snapshot.gateFailedTile());
        assertEquals(1L, snapshot.lastPoorGenWarningWindow());
    }

    @Test
    void modeLoggingGateIsThreadSafe() {
        OceanBathymetryRecovery.resetDiagnosticsForTesting();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[threads];
            for (int thread = 0; thread < threads; thread++) {
                futures[thread] = CompletableFuture.runAsync(() -> OceanBathymetryRecovery.logModeIfActive(11), pool);
            }
            CompletableFuture.allOf(futures).join();
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(OceanBathymetryRecovery.diagnosticsSnapshotForTesting().modeLogged());
    }

    private static int blockFromGlobalPixel(int globalPixel, int zoom) {
        return globalPixel - EarthGenConfig.halfSpanForZoom(zoom);
    }

    private static long pixelKey(int sourceGlobalX, int sourceGlobalY) {
        return ((long) sourceGlobalX << 32) | (sourceGlobalY & 0xFFFFFFFFL);
    }
}
