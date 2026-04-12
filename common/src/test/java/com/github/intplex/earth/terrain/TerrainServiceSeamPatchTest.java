package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainServiceSeamPatchTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void primeMeridianSeamColumnsUseNearestInteriorPixelsAtZoomEleven() throws Exception {
        int zoom = 11;
        double shallowMeters = 75.0;
        double seamMeters = -6000.0;
        installServices(zoom, shallowMeters, seamMeters);

        int leftSeamY = TerrainService.terrainYAtXZ(-1, 0);
        int rightSeamY = TerrainService.terrainYAtXZ(0, 0);
        int rightInteriorY = TerrainService.terrainYAtXZ(1, 0);

        int expectedShallowY = EarthGenConfig.mapMetersToTerrainY(shallowMeters);
        assertEquals(expectedShallowY, leftSeamY);
        assertEquals(expectedShallowY, rightSeamY);
        assertEquals(expectedShallowY, rightInteriorY);
    }

    @Test
    void primeMeridianTwoColumnSeamBandIsPatchedAtZoomTwelve() throws Exception {
        int zoom = 12;
        double shallowMeters = 75.0;
        installServices(zoom, shallowMeters, -9838.0, -1665.0);

        int expectedShallowY = EarthGenConfig.mapMetersToTerrainY(shallowMeters);
        assertEquals(expectedShallowY, TerrainService.terrainYAtXZ(-2, 0));
        assertEquals(expectedShallowY, TerrainService.terrainYAtXZ(-1, 0));
        assertEquals(expectedShallowY, TerrainService.terrainYAtXZ(0, 0));
        assertEquals(expectedShallowY, TerrainService.terrainYAtXZ(1, 0));
    }

    private void installServices(int zoom, double shallowMeters, double seamMeters) {
        installServices(zoom, shallowMeters, seamMeters, seamMeters);
    }

    private void installServices(int zoom, double shallowMeters, double outerSeamMeters, double innerSeamMeters) {
        EarthGenConfig.setActiveZoom(zoom);

        TerrariumTileService activeTerrainService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrarium-active"),
                Executors.newSingleThreadExecutor(),
                key -> createTerrariumSeamPng(zoom, key, shallowMeters, outerSeamMeters, innerSeamMeters),
                64,
                0,
                zoom
            )
        );
        TerrariumTileService recoveryTerrainService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrarium-recovery"),
                Executors.newSingleThreadExecutor(),
                key -> createFlatTerrariumPng(shallowMeters),
                64,
                0,
                OceanBathymetryRecovery.SOURCE_ZOOM
            )
        );
        SurfaceWaterTileService surfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                key -> createSurfaceWaterPng(0xFFFFFFFF),
                64,
                0,
                EarthGenConfig.waterSourceZoomForWorldZoom(zoom)
            )
        );

        TerrainServices.overrideServicesForTesting(activeTerrainService, recoveryTerrainService, null, surfaceWaterTileService);
        TerrainService.clearCaches();
    }

    private static byte[] createTerrariumSeamPng(
        int zoom,
        TileKey key,
        double shallowMeters,
        double outerSeamMeters,
        double innerSeamMeters
    ) throws IOException {
        int shallowRgb = encodeTerrariumRgb(shallowMeters);
        int outerSeamRgb = encodeTerrariumRgb(outerSeamMeters);
        int innerSeamRgb = encodeTerrariumRgb(innerSeamMeters);
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, shallowRgb);
            }
        }

        int seamTileX = EarthGenConfig.tileCountPerAxis(zoom) / 2;
        if (zoom >= 11 && key.x() == seamTileX - 1) {
            for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
                image.setRGB(EarthGenConfig.TILE_SIZE - 1, y, outerSeamRgb);
                if (zoom >= 12) {
                    image.setRGB(EarthGenConfig.TILE_SIZE - 2, y, innerSeamRgb);
                }
            }
        } else if (zoom >= 11 && key.x() == seamTileX) {
            for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
                image.setRGB(0, y, outerSeamRgb);
                if (zoom >= 12) {
                    image.setRGB(1, y, innerSeamRgb);
                }
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] createFlatTerrariumPng(double meters) throws IOException {
        int rgb = encodeTerrariumRgb(meters);
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static byte[] createSurfaceWaterPng(int argb) throws IOException {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < EarthGenConfig.TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.TILE_SIZE; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
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
