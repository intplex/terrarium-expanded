package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceBathymetryRecoveryTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void recoveryAppliesWhenAllGatesPassAtZoomEleven() throws Exception {
        installServices(11, 0.0, -2000.0, 0x000000, 0xFF0000AA);

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(-2000.0), terrainY);
        assertTrue(terrainY < EarthGenConfig.SEA_LEVEL);
    }

    @Test
    void recoveryUsesZoomTenBeforeZoomEightAtZoomTwelve() throws Exception {
        installServices(
            12,
            key -> createTerrariumPngForMeters(0.0),
            Map.of(
                OceanBathymetryRecovery.SOURCE_ZOOM, key -> createTerrariumPngForMeters(-1200.0),
                OceanBathymetryRecovery.FALLBACK_SOURCE_ZOOM, key -> createTerrariumPngForMeters(-3600.0)
            ),
            0x000000,
            key -> createSurfaceWaterPng(0xFF0000AA)
        );

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(-1200.0), terrainY);
    }

    @Test
    void recoveryFallsBackToZoomEightWhenZoomTenReturnsZero() throws Exception {
        installServices(
            12,
            key -> createTerrariumPngForMeters(0.0),
            Map.of(
                OceanBathymetryRecovery.SOURCE_ZOOM, key -> createTerrariumPngForMeters(0.0),
                OceanBathymetryRecovery.FALLBACK_SOURCE_ZOOM, key -> createTerrariumPngForMeters(-2800.0)
            ),
            0x000000,
            key -> createSurfaceWaterPng(0xFF0000AA)
        );

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(-2800.0), terrainY);
    }

    @Test
    void recoveryIgnoresPositiveFallbackSources() throws Exception {
        installServices(
            12,
            key -> createTerrariumPngForMeters(0.0),
            Map.of(
                OceanBathymetryRecovery.SOURCE_ZOOM, key -> createTerrariumPngForMeters(300.0),
                OceanBathymetryRecovery.FALLBACK_SOURCE_ZOOM, key -> createTerrariumPngForMeters(600.0)
            ),
            0x000000,
            key -> createSurfaceWaterPng(0xFF0000AA)
        );

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    @Test
    void waterGateFailureKeepsSeaLevelPlain() throws Exception {
        installServices(11, 0.0, -2000.0, 0x000000, 0xFFFFFFFF);

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    @Test
    void ecoregionGateFailureKeepsSeaLevelPlain() throws Exception {
        installServices(11, 0.0, -2000.0, 0x112233, 0xFF0000AA);

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    @Test
    void zoomTenNeverUsesRecovery() throws Exception {
        installServices(10, 0.0, -2000.0, 0x000000, 0xFF0000AA);

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    @Test
    void nonZeroTerrariumMetersNeverUseRecovery() throws Exception {
        installServices(11, -5.0, -2000.0, 0x000000, 0xFF0000AA);

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(-5.0), terrainY);
    }

    @Test
    void missingSurfaceWaterConfirmationPreventsRecovery() throws Exception {
        installServices(
            11,
            0.0,
            -2000.0,
            0x000000,
            key -> {
                throw new IOException("synthetic water fetch failure");
            }
        );

        int terrainY = TerrainService.terrainYAtXZ(0, 0);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    @Test
    void badTileRecoveryAppliesAtZoomNineFromZoomEight() throws Exception {
        installServices(
            9,
            key -> createTerrariumPngForMeters(0.0),
            key -> createTerrariumPngForMeters(-200.0),
            8,
            0x112233,
            key -> createSurfaceWaterPng(0xFFFFFFFF)
        );

        int blockX = blockFromTilePixel(9, 306, 8);
        int blockZ = blockFromTilePixel(9, 200, 8);
        int terrainY = TerrainService.terrainYAtXZ(blockX, blockZ);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(-200.0), terrainY);
    }

    @Test
    void badTileRecoveryOnlyAppliesForBathymetry() throws Exception {
        installServices(
            9,
            key -> createTerrariumPngForMeters(1000.0),
            key -> createTerrariumPngForMeters(-200.0),
            8,
            0x112233,
            key -> createSurfaceWaterPng(0xFFFFFFFF)
        );

        int blockX = blockFromTilePixel(9, 306, 8);
        int blockZ = blockFromTilePixel(9, 200, 8);
        int terrainY = TerrainService.terrainYAtXZ(blockX, blockZ);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(1000.0), terrainY);
    }

    @Test
    void badTileRecoveryDoesNotAffectTilesOutsideRegistry() throws Exception {
        installServices(
            9,
            key -> createTerrariumPngForMeters(1000.0),
            key -> createTerrariumPngForMeters(-200.0),
            8,
            0x112233,
            key -> createSurfaceWaterPng(0xFFFFFFFF)
        );

        int blockX = blockFromTilePixel(9, 304, 8);
        int blockZ = blockFromTilePixel(9, 200, 8);
        int terrainY = TerrainService.terrainYAtXZ(blockX, blockZ);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(1000.0), terrainY);
    }

    @Test
    void badTileRecoveryFallsBackToOriginalWhenSourceTilesFail() throws Exception {
        installServices(
            9,
            key -> createTerrariumPngForMeters(0.0),
            key -> {
                throw new IOException("synthetic source failure");
            },
            8,
            0x112233,
            key -> createSurfaceWaterPng(0xFFFFFFFF)
        );

        int blockX = blockFromTilePixel(9, 306, 8);
        int blockZ = blockFromTilePixel(9, 200, 8);
        int terrainY = TerrainService.terrainYAtXZ(blockX, blockZ);

        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), terrainY);
    }

    private void installServices(int zoom, double activeMeters, double recoveryMeters, int ecoregionColorRgb, int surfaceWaterArgb) throws IOException {
        installServices(
            zoom,
            key -> createTerrariumPngForMeters(activeMeters),
            key -> createTerrariumPngForMeters(recoveryMeters),
            OceanBathymetryRecovery.SOURCE_ZOOM,
            ecoregionColorRgb,
            key -> createSurfaceWaterPng(surfaceWaterArgb)
        );
    }

    private void installServices(
        int zoom,
        double activeMeters,
        double recoveryMeters,
        int ecoregionColorRgb,
        SurfaceWaterTileService.TileDownloader surfaceWaterDownloader
    ) throws IOException {
        installServices(
            zoom,
            key -> createTerrariumPngForMeters(activeMeters),
            key -> createTerrariumPngForMeters(recoveryMeters),
            OceanBathymetryRecovery.SOURCE_ZOOM,
            ecoregionColorRgb,
            surfaceWaterDownloader
        );
    }

    private void installServices(
        int zoom,
        TerrariumTileService.TileDownloader activeTerrainDownloader,
        TerrariumTileService.TileDownloader sourceTerrainDownloader,
        int sourceZoom,
        int ecoregionColorRgb,
        SurfaceWaterTileService.TileDownloader surfaceWaterDownloader
    ) throws IOException {
        installServices(
            zoom,
            activeTerrainDownloader,
            Map.of(sourceZoom, sourceTerrainDownloader),
            ecoregionColorRgb,
            surfaceWaterDownloader
        );
    }

    private void installServices(
        int zoom,
        TerrariumTileService.TileDownloader activeTerrainDownloader,
        Map<Integer, TerrariumTileService.TileDownloader> sourceTerrainDownloaders,
        int ecoregionColorRgb,
        SurfaceWaterTileService.TileDownloader surfaceWaterDownloader
    ) throws IOException {
        EarthGenConfig.setActiveZoom(zoom);
        TerrariumTileService activeTerrainService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                tempDir.resolve("terrarium-active"),
                Executors.newSingleThreadExecutor(),
                activeTerrainDownloader,
                64,
                0,
                zoom
            )
        );
        Map<Integer, TerrariumTileService> sourceTerrainServices = new LinkedHashMap<>();
        for (Map.Entry<Integer, TerrariumTileService.TileDownloader> entry : sourceTerrainDownloaders.entrySet()) {
            int sourceZoom = entry.getKey();
            sourceTerrainServices.put(
                sourceZoom,
                TerrariumTileService.forTesting(
                    new TerrariumTileService.Config(
                        tempDir.resolve("terrarium-source-" + sourceZoom),
                        Executors.newSingleThreadExecutor(),
                        entry.getValue(),
                        64,
                        0,
                        sourceZoom
                    )
                )
            );
        }
        EcoregionTileService ecoregionTileService = EcoregionTileService.forTesting(
            new EcoregionTileService.Config(
                tempDir.resolve("ecoregion"),
                Executors.newSingleThreadExecutor(),
                key -> createEcoregionPng(ecoregionColorRgb),
                32,
                0
            )
        );
        SurfaceWaterTileService surfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                tempDir.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                surfaceWaterDownloader,
                64,
                0,
                EarthGenConfig.waterSourceZoomForWorldZoom(zoom)
            )
        );

        TerrainServices.overrideSupplementalTerrainServicesForTesting(
            activeTerrainService,
            sourceTerrainServices,
            ecoregionTileService,
            surfaceWaterTileService
        );
        TerrainService.clearCaches();
    }

    private static int blockFromTilePixel(int zoom, int tileCoordinate, int pixelCoordinate) {
        int globalPixel = tileCoordinate * EarthGenConfig.TILE_SIZE + pixelCoordinate;
        return globalPixel - EarthGenConfig.halfSpanForZoom(zoom);
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

    private static byte[] createEcoregionPng(int colorRgb) throws IOException {
        BufferedImage image = new BufferedImage(
            EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE,
            EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE,
            BufferedImage.TYPE_INT_RGB
        );
        for (int y = 0; y < EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE; y++) {
            for (int x = 0; x < EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE; x++) {
                image.setRGB(x, y, colorRgb);
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
