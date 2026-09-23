package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceElevationFailureTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void failedSnapshotDoesNotCacheFallbackAndCanRecoverWithoutClearingCaches() throws Exception {
        AtomicBoolean unavailable = new AtomicBoolean(true);
        IOException failure = new IOException("temporary elevation outage");
        byte[] validTile = terrainPng();
        installService(key -> {
            if (unavailable.get()) {
                throw failure;
            }
            return validTile;
        });

        for (int attempt = 0; attempt < 2; attempt++) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> TerrainService.terrainYAtXZ(0, 0));
            assertTrue(thrown.getMessage().contains("Required terrain elevation unavailable"));
            assertSame(failure, thrown.getCause().getCause());
        }

        unavailable.set(false);
        int expectedY = EarthGenConfig.mapMetersToTerrainY(1000.0);
        assertEquals(expectedY, TerrainService.terrainYAtXZ(0, 0));
        try (var otherThread = Executors.newSingleThreadExecutor()) {
            assertEquals(expectedY, CompletableFuture.supplyAsync(() -> TerrainService.terrainYAtXZ(0, 0), otherThread).join());
        }
    }

    @Test
    void decodeFailureAbortsSamplingAndCanRecover() throws Exception {
        AtomicBoolean corrupt = new AtomicBoolean(true);
        byte[] validTile = terrainPng();
        installService(key -> corrupt.get() ? new byte[] {0, 1, 2} : validTile);
        EarthSamplingFacade.LocalTileCaches caches = EarthSamplingFacade.chunkLocalCaches();

        assertThrows(IllegalStateException.class, () -> EarthSamplingFacade.sampleTerrain(0, 0, caches));
        corrupt.set(false);
        assertEquals(EarthGenConfig.mapMetersToTerrainY(1000.0), EarthSamplingFacade.sampleTerrain(0, 0, caches).terrainY());
    }

    @Test
    void knownMissingElevationNeverBecomesSeaLevelTerrain() throws Exception {
        AtomicInteger downloads = new AtomicInteger();
        installService(key -> {
            downloads.incrementAndGet();
            throw new RemotePngTileStore.HttpStatusException(404, URI.create("https://example.invalid/tile.png"));
        });

        for (int attempt = 0; attempt < 2; attempt++) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> TerrainService.terrainYAtXZ(0, 0));
            assertTrue(thrown.getCause() instanceof RemotePngTileStore.MissingTileException);
        }
        assertEquals(1, downloads.get(), "the persistent missing marker still avoids repeated HTTP requests");
    }

    private void installService(TerrariumTileService.TileDownloader downloader) throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(TerrariumRuntimeConfig.FILE_NAME), "inland_water.enabled=false\n");
        TerrainServices.bootstrap(tempDir);
        TerrariumTileService service = TerrariumTileService.forTesting(new TerrariumTileService.Config(
            tempDir.resolve("terrain"), Executors.newSingleThreadExecutor(), downloader, 16, 0, EarthGenConfig.DEFAULT_ZOOM
        ));
        TerrainServices.overrideServicesForTesting(service, null, null, null);
    }

    private static byte[] terrainPng() throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        int rgb = (32768 + 1000) << 8;
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("Unable to encode test tile");
        }
        return bytes.toByteArray();
    }
}
