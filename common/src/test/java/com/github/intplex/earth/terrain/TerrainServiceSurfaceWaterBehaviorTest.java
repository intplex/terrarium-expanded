package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceSurfaceWaterBehaviorTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void outOfCoverageChunksSkipSurfaceWaterFetches() throws Exception {
        AtomicInteger waterFetches = new AtomicInteger();
        installServices(
            key -> createTerrariumPngForMeters(250.0),
            key -> {
                waterFetches.incrementAndGet();
                return createSurfaceWaterPng(0xFF0000AA);
            }
        );

        EarthGenConfig.BlockCoordinates target = EarthGenConfig.geoToBlock(-70.0, 0.0)
            .orElseThrow(() -> new IllegalStateException("Expected -70 latitude to be projectable"));

        TerrainService.clearCaches();
        WaterBodyKind kind = TerrainService.inlandWaterKindAtXZ(target.x(), target.z());

        assertEquals(WaterBodyKind.NONE, kind);
        assertEquals(0, waterFetches.get(), "Surface-water downloader should not be called outside configured coverage");
    }

    @Test
    void missingSurfaceWaterTileSkipsInlandAnalysisForEntireChunkSnapshot() throws Exception {
        AtomicInteger missingTileFetches = new AtomicInteger();
        TileKey missingTile = new TileKey(127, 127);

        installServices(
            key -> createTerrariumPngForMeters(250.0),
            key -> {
                if (key.equals(missingTile)) {
                    missingTileFetches.incrementAndGet();
                    throw new RemotePngTileStore.HttpStatusException(404, URI.create("https://example.invalid/" + key.x() + "/" + key.y() + ".png"));
                }
                return createSurfaceWaterPng(0xFF0000AA);
            }
        );

        TerrainService.clearCaches();
        WaterBodyKind center = TerrainService.inlandWaterKindAtXZ(0, 0);
        WaterBodyKind nearby = TerrainService.inlandWaterKindAtXZ(8, 8);

        assertEquals(WaterBodyKind.NONE, center, "Missing sampled surface-water tile should disable inland water for the chunk snapshot");
        assertEquals(WaterBodyKind.NONE, nearby);
        assertEquals(1, missingTileFetches.get(), "Missing tile should be attempted once for the chunk snapshot");
    }

    @Test
    void failedSurfaceWaterTileIsAttemptedOncePerChunkSnapshot() throws Exception {
        AtomicInteger failedTileFetches = new AtomicInteger();
        TileKey failedTile = new TileKey(127, 127);

        installServices(
            key -> createTerrariumPngForMeters(250.0),
            key -> {
                if (key.equals(failedTile)) {
                    failedTileFetches.incrementAndGet();
                    throw new IOException("Synthetic network failure");
                }
                return createSurfaceWaterPng(0xFF0000AA);
            }
        );

        TerrainService.clearCaches();
        WaterBodyKind center = TerrainService.inlandWaterKindAtXZ(0, 0);
        WaterBodyKind nearby = TerrainService.inlandWaterKindAtXZ(8, 8);

        assertEquals(WaterBodyKind.NONE, center);
        assertEquals(WaterBodyKind.NONE, nearby);
        assertEquals(1, failedTileFetches.get(), "Failed tile should not be repeatedly refetched while building one chunk snapshot");
    }

    private void installServices(TerrariumTileService.TileDownloader terrainDownloader, SurfaceWaterTileService.TileDownloader waterDownloader) {
        TerrariumTileService terrariumTileService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrarium"),
                Executors.newSingleThreadExecutor(),
                terrainDownloader,
                64,
                0,
                EarthGenConfig.activeZoom()
            )
        );
        SurfaceWaterTileService surfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                waterDownloader,
                64,
                0,
                EarthGenConfig.activeWaterZoom()
            )
        );
        TerrainServices.overrideServicesForTesting(terrariumTileService, null, null, surfaceWaterTileService);
    }

    private static byte[] createTerrariumPngForMeters(double meters) throws IOException {
        int rgb = encodeTerrariumRgb(meters);
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
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

    private static byte[] createSurfaceWaterPng(int argb) throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
