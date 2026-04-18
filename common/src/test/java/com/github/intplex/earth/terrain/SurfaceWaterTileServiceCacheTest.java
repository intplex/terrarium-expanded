package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceWaterTileServiceCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void memoryCacheAvoidsDuplicateFetches() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        SurfaceWaterTileService.TileDownloader downloader = key -> {
            downloads.incrementAndGet();
            return createSurfaceWaterPng(0xFF0000AA);
        };

        SurfaceWaterTileService service = newService(tempDir.resolve("cache"), downloader);
        TileKey key = new TileKey(10, 10);
        try {
            service.getOrLoad(key);
            service.getOrLoad(key);
            assertEquals(1, downloads.get());
        } finally {
            service.close();
        }
    }

    @Test
    void memoryCacheEntriesExpireAfterIdleTtl() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        SurfaceWaterTileService.TileDownloader downloader = key -> {
            downloads.incrementAndGet();
            return createSurfaceWaterPng(0xFF0000AA);
        };

        SurfaceWaterTileService service = newService(tempDir.resolve("cache-ttl"), downloader, 1);
        TileKey key = new TileKey(11, 11);
        try {
            SurfaceWaterTile first = service.getOrLoad(key);
            Thread.sleep(1_250L);
            SurfaceWaterTile second = service.getOrLoad(key);
            assertEquals(1, downloads.get(), "expired entries should be reloaded from disk cache without downloader refetch");
            assertNotSame(first, second, "expired in-memory entries should not return the same tile object");
        } finally {
            service.close();
        }
    }

    @Test
    void diskCacheSurvivesServiceRestart() throws Exception {
        Path cacheRoot = tempDir.resolve("cache");
        AtomicInteger firstDownloaderCalls = new AtomicInteger();
        SurfaceWaterTileService first = newService(cacheRoot, key -> {
            firstDownloaderCalls.incrementAndGet();
            return createSurfaceWaterPng(0xFF0000AA);
        });

        TileKey key = new TileKey(20, 20);
        first.getOrLoad(key);
        first.close();
        assertEquals(1, firstDownloaderCalls.get());

        AtomicInteger secondDownloaderCalls = new AtomicInteger();
        SurfaceWaterTileService second = newService(cacheRoot, k -> {
            secondDownloaderCalls.incrementAndGet();
            return createSurfaceWaterPng(0xFF0000AA);
        });
        try {
            second.getOrLoad(key);
            assertEquals(0, secondDownloaderCalls.get());
        } finally {
            second.close();
        }
    }

    @Test
    void inFlightRequestsAreDeduplicated() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        SurfaceWaterTileService.TileDownloader downloader = key -> {
            downloads.incrementAndGet();
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", exception);
            }
            return createSurfaceWaterPng(0xFF0000AA);
        };

        SurfaceWaterTileService service = newService(tempDir.resolve("cache"), downloader);
        ExecutorService requestPool = Executors.newFixedThreadPool(12);
        TileKey key = new TileKey(30, 30);

        try {
            List<CompletableFuture<SurfaceWaterTile>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> service.getOrLoad(key), requestPool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            assertEquals(1, downloads.get());
        } finally {
            requestPool.shutdownNow();
            service.close();
        }
    }

    @Test
    void tileUsesRgbClassifierInsteadOfAlphaOnly() throws Exception {
        SurfaceWaterTileService service = newService(tempDir.resolve("cache"), key -> createSurfaceWaterPng(0xFFFFFFFF));
        TileKey key = new TileKey(40, 40);
        try {
            SurfaceWaterTile tile = service.getOrLoad(key);
            assertFalse(tile.isWaterAt(0, 0, 10));
        } finally {
            service.close();
        }

        SurfaceWaterTileService blueService = newService(tempDir.resolve("cache-blue"), k -> createSurfaceWaterPng(0x7F0000AA));
        try {
            SurfaceWaterTile tile = blueService.getOrLoad(key);
            assertTrue(tile.isWaterAt(0, 0, 10));
        } finally {
            blueService.close();
        }
    }

    @Test
    void missingTilesAreCachedAndNotRefetched() throws Exception {
        Path cacheRoot = tempDir.resolve("cache-missing");
        TileKey key = new TileKey(80, 191);
        URI uri = URI.create("https://example.invalid/tiles2021/seasonality/8/80/191.png");

        AtomicInteger firstDownloaderCalls = new AtomicInteger();
        SurfaceWaterTileService first = newService(cacheRoot, k -> {
            firstDownloaderCalls.incrementAndGet();
            throw new RemotePngTileStore.HttpStatusException(404, uri);
        });
        try {
            assertThrows(RemotePngTileStore.MissingTileException.class, () -> first.getOrLoad(key));
            assertThrows(RemotePngTileStore.MissingTileException.class, () -> first.getOrLoad(key));
            assertEquals(1, firstDownloaderCalls.get(), "missing tile should be fetched once per service lifecycle");
        } finally {
            first.close();
        }

        AtomicInteger secondDownloaderCalls = new AtomicInteger();
        SurfaceWaterTileService second = newService(cacheRoot, k -> {
            secondDownloaderCalls.incrementAndGet();
            throw new RemotePngTileStore.HttpStatusException(404, uri);
        });
        try {
            assertThrows(RemotePngTileStore.MissingTileException.class, () -> second.getOrLoad(key));
            assertEquals(0, secondDownloaderCalls.get(), "missing tile marker should survive restart and skip fetch");
        } finally {
            second.close();
        }
    }

    private static SurfaceWaterTileService newService(Path cacheRoot, SurfaceWaterTileService.TileDownloader downloader) {
        return newService(cacheRoot, downloader, SurfaceWaterTileService.DEFAULT_MEMORY_CACHE_TTL_SECONDS);
    }

    @Test
    void weightedCacheRespectsMaxWeightBudget() throws Exception {
        SurfaceWaterTileService service = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("cache-weighted"),
                SurfaceWaterTileService.createDefaultExecutor(),
                key -> createSurfaceWaterPng(0xFF0000AA),
                SurfaceWaterTileService.APPROX_TILE_BYTES,
                120,
                SurfaceWaterTileService.PREFETCH_RADIUS,
                EarthGenConfig.DEFAULT_ZOOM,
                true
            )
        );
        try {
            service.getOrLoad(new TileKey(1, 1));
            service.getOrLoad(new TileKey(2, 2));
            service.getOrLoad(new TileKey(1, 1));

            assertTrue(service.currentMemoryCacheWeightBytes() <= SurfaceWaterTileService.APPROX_TILE_BYTES);
        } finally {
            service.close();
        }
    }

    private static SurfaceWaterTileService newService(
        Path cacheRoot,
        SurfaceWaterTileService.TileDownloader downloader,
        int memoryCacheTtlSeconds
    ) {
        return SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                cacheRoot,
                SurfaceWaterTileService.createDefaultExecutor(),
                downloader,
                32,
                memoryCacheTtlSeconds,
                SurfaceWaterTileService.PREFETCH_RADIUS,
                EarthGenConfig.DEFAULT_ZOOM
            )
        );
    }

    private static byte[] createSurfaceWaterPng(int argb) throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
            for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
                image.setRGB(x, y, argb);
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}
