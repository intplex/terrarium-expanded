package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceCacheEvictionTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void chunkSnapshotCacheEntriesExpireAfterIdleTtl() throws Exception {
        bootstrapWithConfig(
            "memory.snapshot_ttl_seconds=1\n"
                + "inland_water.enabled=false\n"
        );
        installTerrainService();

        Object first = TerrainService.snapshotIdentityForTesting(0, 0);
        Thread.sleep(1_250L);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Object second = CompletableFuture.supplyAsync(() -> TerrainService.snapshotIdentityForTesting(0, 0), pool).join();
            assertNotSame(first, second, "chunk snapshot should be rebuilt after TTL expiry");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void chunkLocalTileCachesAreClearedAfterIdleWindow() throws Exception {
        bootstrapWithConfig(
            "memory.local_chunk_entries=4\n"
                + "memory.local_idle_seconds=1\n"
                + "inland_water.enabled=false\n"
        );
        installTerrainService();

        EarthSamplingFacade.LocalTileCaches caches = EarthSamplingFacade.chunkLocalCaches();
        EarthSamplingFacade.sampleTerrain(0, 0, caches);
        assertTrue(EarthSamplingFacade.chunkLocalCaches().totalEntries() > 0, "sampling should populate chunk-local tile caches");

        Thread.sleep(1_250L);
        assertEquals(0, EarthSamplingFacade.chunkLocalCaches().totalEntries(), "idle chunk-local caches should be cleared");
    }

    private void bootstrapWithConfig(String body) throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(TerrariumRuntimeConfig.FILE_NAME), body);
        TerrainServices.bootstrap(tempDir);
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
    }

    private void installTerrainService() throws IOException {
        byte[] terrainTile = createTerrariumTilePng(150.0);
        TerrariumTileService service = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrain-cache"),
                Executors.newSingleThreadExecutor(),
                key -> terrainTile,
                16,
                0,
                EarthGenConfig.DEFAULT_ZOOM
            )
        );
        TerrainServices.overrideServicesForTesting(service, null, null, null);
    }

    private static byte[] createTerrariumTilePng(double meters) throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        int packed = encodeTerrariumRgb(meters);
        for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
            for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
                image.setRGB(x, y, packed);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Unable to encode Terrarium tile PNG");
        }
        return output.toByteArray();
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
}
