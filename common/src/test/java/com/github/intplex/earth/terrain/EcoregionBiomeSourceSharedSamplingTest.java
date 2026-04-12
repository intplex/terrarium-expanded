package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.biome.EcoregionBiomeMappings;
import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EcoregionBiomeSourceSharedSamplingTest {
    private static final short[] TEMPERATE_TEST_GRID = new short[180 * 360];

    static {
        java.util.Arrays.fill(TEMPERATE_TEST_GRID, (short) 1400);
    }

    @TempDir
    Path tempDir;
    private int installSequence;

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void tearDown() {
        OceanSurfaceTemperatureService.setPackedSstForTesting(null);
        TerrainServices.resetForTesting();
    }

    @Test
    void biomeFallbackParityTracksTerrainChannelAtRepresentativePoints() throws Exception {
        installServices(-1500.0, -2000.0, 0x123456, 0xFFFFFFFF, 11);
        OceanSurfaceTemperatureService.setPackedSstForTesting(TEMPERATE_TEST_GRID.clone());

        EcoregionBiomeMappings.ResolvedBiomeMapping mapping = mapping(Map.of());
        EcoregionBiomeSource source = createSource(mapping, 11);

        assertParityAt(source, mapping, 0, 0);
        assertParityAt(source, mapping, 192, -320);
        assertParityAt(source, mapping, -2048, 1536);
    }

    @Test
    void biomeTerrainSamplerHonorsRecoveryGatesFromSharedFacade() throws Exception {
        OceanSurfaceTemperatureService.setPackedSstForTesting(TEMPERATE_TEST_GRID.clone());
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping = mapping(Map.of());

        installServices(0.0, -2000.0, 0x000000, 0xFF0000AA, 11);
        EcoregionBiomeSource recoveredSource = createSource(mapping, 11);
        int recoveredTerrainY = TerrainService.terrainYAtXZ(0, 0);
        assertEquals(EarthGenConfig.mapMetersToTerrainY(-2000.0), recoveredTerrainY);
        Holder<Biome> recovered = recoveredSource.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(expectedOceanFallbackBiome(mapping, recoveredTerrainY), recovered);

        TerrainServices.resetForTesting();
        installServices(0.0, -2000.0, 0x112233, 0xFF0000AA, 11);
        EcoregionBiomeSource gatedSource = createSource(mapping, 11);
        int gatedTerrainY = TerrainService.terrainYAtXZ(0, 0);
        assertEquals(EarthGenConfig.mapMetersToTerrainY(0.0), gatedTerrainY);
        Holder<Biome> gated = gatedSource.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(expectedOceanFallbackBiome(mapping, gatedTerrainY), gated);
    }

    private void assertParityAt(EcoregionBiomeSource source, EcoregionBiomeMappings.ResolvedBiomeMapping mapping, int blockX, int blockZ) {
        int terrainY = TerrainService.terrainYAtXZ(blockX, blockZ);
        Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(blockX), 0, QuartPos.fromBlock(blockZ), null);
        assertEquals(expectedOceanFallbackBiome(mapping, terrainY), biome);
    }

    private static Holder<Biome> expectedOceanFallbackBiome(EcoregionBiomeMappings.ResolvedBiomeMapping mapping, int terrainY) {
        int continentalShelfThreshold = EarthGenConfig.mapMetersToTerrainY(-200.0);
        return terrainY <= continentalShelfThreshold ? mapping.deepOceanBiome() : mapping.oceanBiome();
    }

    private void installServices(
        double activeMeters,
        double recoveryMeters,
        int ecoregionColorRgb,
        int surfaceWaterArgb,
        int zoom
    ) throws IOException {
        EarthGenConfig.setActiveZoom(zoom);
        Path runRoot = tempDir.resolve("run-" + installSequence++);
        TerrariumTileService activeTerrainService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                runRoot.resolve("terrarium-active"),
                Executors.newSingleThreadExecutor(),
                key -> createTerrariumPngForMeters(activeMeters),
                64,
                0,
                zoom
            )
        );
        TerrariumTileService recoveryTerrainService = TerrariumTileService.forTesting(
            new TerrariumTileService.Config(
                runRoot.resolve("terrarium-recovery"),
                Executors.newSingleThreadExecutor(),
                key -> createTerrariumPngForMeters(recoveryMeters),
                64,
                0,
                OceanBathymetryRecovery.SOURCE_ZOOM
            )
        );
        EcoregionTileService ecoregionTileService = EcoregionTileService.forTesting(
            new EcoregionTileService.Config(
                runRoot.resolve("ecoregion"),
                Executors.newSingleThreadExecutor(),
                key -> createEcoregionPng(ecoregionColorRgb),
                32,
                0
            )
        );
        SurfaceWaterTileService surfaceWaterTileService = SurfaceWaterTileService.forTesting(
            new SurfaceWaterTileService.Config(
                runRoot.resolve("surface-water"),
                Executors.newSingleThreadExecutor(),
                key -> createSurfaceWaterPng(surfaceWaterArgb),
                64,
                0,
                EarthGenConfig.waterSourceZoomForWorldZoom(zoom)
            )
        );

        TerrainServices.overrideServicesForTesting(
            activeTerrainService,
            recoveryTerrainService,
            ecoregionTileService,
            surfaceWaterTileService
        );
        TerrainService.clearCaches();
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

    private static EcoregionBiomeMappings.ResolvedBiomeMapping mapping(Map<Integer, Holder<Biome>> byColor) {
        Holder<Biome> ocean = dummyBiomeHolder();
        Holder<Biome> coldOcean = dummyBiomeHolder();
        Holder<Biome> lukewarmOcean = dummyBiomeHolder();
        Holder<Biome> warmOcean = dummyBiomeHolder();
        Holder<Biome> frozenOcean = dummyBiomeHolder();
        Holder<Biome> deepOcean = dummyBiomeHolder();
        Holder<Biome> deepColdOcean = dummyBiomeHolder();
        Holder<Biome> deepLukewarmOcean = dummyBiomeHolder();
        Holder<Biome> deepFrozenOcean = dummyBiomeHolder();
        Holder<Biome> river = dummyBiomeHolder();
        Holder<Biome> frozenRiver = dummyBiomeHolder();
        java.util.LinkedHashSet<Holder<Biome>> possible = new java.util.LinkedHashSet<>();
        possible.add(ocean);
        possible.add(coldOcean);
        possible.add(lukewarmOcean);
        possible.add(warmOcean);
        possible.add(frozenOcean);
        possible.add(deepOcean);
        possible.add(deepColdOcean);
        possible.add(deepLukewarmOcean);
        possible.add(deepFrozenOcean);
        possible.add(river);
        possible.add(frozenRiver);
        possible.addAll(byColor.values());
        return new EcoregionBiomeMappings.ResolvedBiomeMapping(
            Map.copyOf(byColor),
            ocean,
            coldOcean,
            lukewarmOcean,
            warmOcean,
            frozenOcean,
            deepOcean,
            deepColdOcean,
            deepLukewarmOcean,
            deepFrozenOcean,
            river,
            frozenRiver,
            Set.copyOf(possible)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Holder<Biome> dummyBiomeHolder() {
        return (Holder<Biome>) (Holder) Holder.direct(new Object());
    }

    private static EcoregionBiomeSource createSource(EcoregionBiomeMappings.ResolvedBiomeMapping mapping, int zoom) {
        return new EcoregionBiomeSource(
            mapping,
            EcoregionBiomeSource.SamplingAdapters.defaults(),
            new EarthGenerationProfile(
                zoom,
                EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
                EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
            )
        );
    }
}
