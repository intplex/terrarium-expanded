package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainServiceGoldenCoordinatesTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void representativeCoordinatesStayStableForFlatTerrainProfile() throws Exception {
        double meters = 350.0;
        int expectedTerrainY = EarthGenConfig.mapMetersToTerrainY(meters);

        installFlatServices(meters);
        TerrainService.clearCaches();

        assertColumn(0, 0, expectedTerrainY);
        assertColumn(192, -320, expectedTerrainY);
        assertColumn(-2048, 1536, expectedTerrainY);

        int edge = EarthGenConfig.halfSpan() - 1;
        assertColumn(edge, edge, expectedTerrainY);
        assertColumn(-edge, -edge, expectedTerrainY);
    }

    private void assertColumn(int blockX, int blockZ, int expectedTerrainY) {
        MutableFunctionContext context = new MutableFunctionContext();
        context.set(blockX, EarthGenConfig.SEA_LEVEL, blockZ);

        assertEquals(expectedTerrainY, TerrainService.terrainYAtXZ(blockX, blockZ));
        assertEquals(expectedTerrainY, TerrainService.effectiveSolidTopYAtXZ(blockX, blockZ));
        assertEquals(-1.0, TerrainService.erosionAtXZ(context), 1.0e-6);
        assertEquals(0.0, TerrainService.continentalnessAtXZ(context), 1.0e-6);
        assertEquals(0.0, TerrainService.weirdnessAtXZ(context), 1.0e-6);
        assertEquals(TerrainService.depthFromTerrainY(expectedTerrainY), TerrainService.depthDensityAtY(context), 1.0e-6);
    }

    private void installFlatServices(double meters) {
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
        TerrariumTileService terrariumTileService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrarium"),
                Executors.newSingleThreadExecutor(),
                key -> createTerrariumPngForMeters(meters),
                64,
                0,
                EarthGenConfig.activeZoom()
            )
        );
        SurfaceWaterTileService surfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                key -> createSurfaceWaterPng(0xFFFFFFFF),
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
