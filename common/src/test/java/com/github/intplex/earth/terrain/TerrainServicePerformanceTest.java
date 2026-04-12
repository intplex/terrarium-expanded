package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServicePerformanceTest {
    private static final int TILE_MEMORY_CACHE_ENTRIES = 4096;
    private static final int TEST_PREFETCH_RADIUS = 0;
    private static final int QUERY_REPETITIONS = 3;
    private static final int GRID_MIN = -64;
    private static final int GRID_MAX_EXCLUSIVE = 64;
    private static final int GRID_STEP = 2;
    private static final int CONCURRENT_THREADS = 8;

    private static volatile long blackhole;

    private static Path tempDir;
    private static CountingTileDownloader terrainDownloader;
    private static CountingSurfaceWaterDownloader surfaceWaterDownloader;
    private static TerrariumTileService testTileService;
    private static SurfaceWaterTileService testSurfaceWaterTileService;
    private static int[] queryX;
    private static int[] queryZ;

    @BeforeAll
    static void setup() throws IOException {
        TerrainServices.resetForTesting();
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
        tempDir = Files.createTempDirectory("terrain-service-perf");
        terrainDownloader = new CountingTileDownloader(buildSyntheticTerrariumTilePng());
        surfaceWaterDownloader = new CountingSurfaceWaterDownloader(buildSyntheticSurfaceWaterTilePng());
        testTileService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir,
                Executors.newSingleThreadExecutor(),
                terrainDownloader,
                TILE_MEMORY_CACHE_ENTRIES,
                TEST_PREFETCH_RADIUS,
                EarthGenConfig.activeZoom()
            )
        );
        testSurfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                surfaceWaterDownloader,
                TILE_MEMORY_CACHE_ENTRIES,
                TEST_PREFETCH_RADIUS,
                EarthGenConfig.activeWaterZoom()
            )
        );
        TerrainServices.overrideServicesForTesting(testTileService, null, null, testSurfaceWaterTileService);
        queryX = buildAxisCoordinates();
        queryZ = buildAxisCoordinates();
    }

    @AfterAll
    static void tearDown() throws IOException {
        TerrainServices.resetForTesting();
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
        }
    }

    @BeforeEach
    void beforeEach() {
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
        TerrainService.clearCaches();
    }

    @Test
    void cachedQueriesShouldOutperformColdQueriesWithoutAdditionalTileFetches() {
        warmTileMemory();
        TerrainService.clearCaches();
        terrainDownloader.reset();
        surfaceWaterDownloader.reset();

        long coldNanos = runAllChannels(queryX, queryZ, QUERY_REPETITIONS);
        long warmNanos = runAllChannels(queryX, queryZ, QUERY_REPETITIONS);

        assertEquals(
            0,
            terrainDownloader.fetchCount(),
            "Performance pass should not fetch Terrarium tiles; all tile data must come from memory cache"
        );
        assertEquals(
            0,
            surfaceWaterDownloader.fetchCount(),
            "Performance pass should not fetch surface-water tiles; all tile data must come from memory cache"
        );
        assertTrue(
            warmNanos < coldNanos,
            "Warm pass should be faster than cold pass (cold=" + coldNanos + "ns, warm=" + warmNanos + "ns)"
        );

        long pointCount = (long) queryX.length * queryZ.length * QUERY_REPETITIONS;
        double warmNsPerPoint = warmNanos / (double) pointCount;
        assertTrue(
            warmNsPerPoint < 280_000.0,
            "Warm pass exceeded regression budget: " + warmNsPerPoint + "ns/point"
        );
    }

    @Test
    void concurrentWarmQueriesRemainFetchFreeAndScaleReasonably() {
        warmTileMemory();
        TerrainService.clearCaches();
        terrainDownloader.reset();
        surfaceWaterDownloader.reset();

        long baselineWarmNanos = runAllChannels(queryX, queryZ, QUERY_REPETITIONS);
        long concurrentNanos = runAllChannelsConcurrently(queryX, queryZ, QUERY_REPETITIONS, CONCURRENT_THREADS);

        assertEquals(0, terrainDownloader.fetchCount(), "Concurrent warm pass should not fetch Terrarium tiles");
        assertEquals(0, surfaceWaterDownloader.fetchCount(), "Concurrent warm pass should not fetch surface-water tiles");

        long pointCount = (long) queryX.length * queryZ.length * QUERY_REPETITIONS * CONCURRENT_THREADS;
        double concurrentNsPerPoint = concurrentNanos / (double) pointCount;
        assertTrue(
            concurrentNsPerPoint < 420_000.0,
            "Concurrent warm pass exceeded contention budget: " + concurrentNsPerPoint + "ns/point"
                + " (baseline warm total=" + baselineWarmNanos + "ns, concurrent total=" + concurrentNanos + "ns)"
        );
    }

    private static void warmTileMemory() {
        TileKey terrainCenter = EarthGenConfig.projectBlockToTerrainTile(0, 0)
            .orElseThrow(() -> new IllegalStateException("Origin must be within Terrarium bounds"))
            .tileKey();
        TileKey waterCenter = EarthGenConfig.projectBlockToTile(0, 0, EarthGenConfig.activeWaterZoom())
            .orElseThrow(() -> new IllegalStateException("Origin must be within surface-water bounds"))
            .tileKey();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                TerrainServices.tileService().requireTile(new TileKey(terrainCenter.x() + dx, terrainCenter.y() + dz));
                TerrainServices.surfaceWaterTileService().requireTile(new TileKey(waterCenter.x() + dx, waterCenter.y() + dz));
            }
        }
    }

    private static long runAllChannels(int[] xs, int[] zs, int repetitions) {
        MutableFunctionContext context = new MutableFunctionContext();
        long checksum = 0L;
        long start = System.nanoTime();

        for (int repeat = 0; repeat < repetitions; repeat++) {
            for (int x : xs) {
                for (int z : zs) {
                    int y = EarthGenConfig.SEA_LEVEL + ((x + z + repeat) & 31) - 16;
                    context.set(x, y, z);
                    checksum ^= Double.doubleToRawLongBits(TerrainService.continentalnessAtXZ(context));
                    checksum ^= Double.doubleToRawLongBits(TerrainService.erosionAtXZ(context));
                    checksum ^= Double.doubleToRawLongBits(TerrainService.weirdnessAtXZ(context));
                    checksum ^= Double.doubleToRawLongBits(TerrainService.depthDensityAtY(context));
                    checksum ^= Double.doubleToRawLongBits(TerrainService.envelopeDensityAtXYZ(context));
                }
            }
        }

        long elapsed = System.nanoTime() - start;
        blackhole ^= checksum;
        return elapsed;
    }

    private static long runAllChannelsConcurrently(int[] xs, int[] zs, int repetitions, int threadCount) {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        try {
            long start = System.nanoTime();
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            for (int thread = 0; thread < threadCount; thread++) {
                futures.add(CompletableFuture.supplyAsync(() -> runAllChannels(xs, zs, repetitions), pool));
            }

            long checksum = 0L;
            for (CompletableFuture<Long> future : futures) {
                checksum ^= future.join();
            }
            blackhole ^= checksum;
            return System.nanoTime() - start;
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static int[] buildAxisCoordinates() {
        int count = (GRID_MAX_EXCLUSIVE - GRID_MIN) / GRID_STEP;
        int[] values = new int[count];
        int index = 0;
        for (int value = GRID_MIN; value < GRID_MAX_EXCLUSIVE; value += GRID_STEP) {
            values[index++] = value;
        }
        return values;
    }

    private static byte[] buildSyntheticTerrariumTilePng() throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                double meters = syntheticMeters(x, y);
                image.setRGB(x, y, encodeTerrariumRgb(meters));
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Unable to encode synthetic Terrarium PNG");
        }
        return output.toByteArray();
    }

    private static byte[] buildSyntheticSurfaceWaterTilePng() throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        int dry = 0xFFFFFFFF;
        int water = 0xFF0000AA;
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, x % 32 == 0 ? water : dry);
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Unable to encode synthetic surface-water PNG");
        }
        return output.toByteArray();
    }

    private static double syntheticMeters(int x, int y) {
        double value =
            2200.0 * Math.sin(x * 0.035)
                + 1600.0 * Math.cos(y * 0.028)
                + 900.0 * Math.sin((x + y) * 0.018)
                - 800.0;
        return Math.max(-3500.0, Math.min(8500.0, value));
    }

    private static int encodeTerrariumRgb(double meters) {
        double shifted = meters + 32768.0;
        int red = clampByte((int) Math.floor(shifted / 256.0));
        double remainder = shifted - red * 256.0;
        int green = clampByte((int) Math.floor(remainder));
        int blue = clampByte((int) Math.floor((remainder - green) * 256.0));
        return (red << 16) | (green << 8) | blue;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class CountingTileDownloader implements TerrariumTileService.TileDownloader {
        private final byte[] pngBytes;
        private final AtomicInteger fetchCount = new AtomicInteger();

        private CountingTileDownloader(byte[] pngBytes) {
            this.pngBytes = pngBytes;
        }

        @Override
        public byte[] fetch(TileKey key) {
            fetchCount.incrementAndGet();
            return pngBytes;
        }

        private int fetchCount() {
            return fetchCount.get();
        }

        private void reset() {
            fetchCount.set(0);
        }
    }

    private static final class CountingSurfaceWaterDownloader implements SurfaceWaterTileService.TileDownloader {
        private final byte[] pngBytes;
        private final AtomicInteger fetchCount = new AtomicInteger();

        private CountingSurfaceWaterDownloader(byte[] pngBytes) {
            this.pngBytes = pngBytes;
        }

        @Override
        public byte[] fetch(TileKey key) {
            fetchCount.incrementAndGet();
            return pngBytes;
        }

        private int fetchCount() {
            return fetchCount.get();
        }

        private void reset() {
            fetchCount.set(0);
        }
    }

    private static final class MutableFunctionContext implements DensityFunction.FunctionContext {
        private int blockX;
        private int blockY;
        private int blockZ;

        private void set(int blockX, int blockY, int blockZ) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        @Override
        public int blockX() {
            return blockX;
        }

        @Override
        public int blockY() {
            return blockY;
        }

        @Override
        public int blockZ() {
            return blockZ;
        }
    }
}
