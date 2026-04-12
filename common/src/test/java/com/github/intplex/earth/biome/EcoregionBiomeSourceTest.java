package com.github.intplex.earth.biome;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.OceanSurfaceTemperatureService;
import com.github.intplex.earth.terrain.WaterBodyKind;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionBiomeSourceTest {
    private static final short[] TEMPERATE_TEST_GRID = new short[180 * 360];
    private static final int MODE_TEST_COLOR = 0xABCDEF;

    static {
        java.util.Arrays.fill(TEMPERATE_TEST_GRID, (short) 1400); // 14.00 C
    }

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void injectTemperateSstGrid() {
        OceanSurfaceTemperatureService.setPackedSstForTesting(TEMPERATE_TEST_GRID.clone());
    }

    @AfterEach
    void clearInjectedSstGrid() {
        OceanSurfaceTemperatureService.setPackedSstForTesting(null);
    }

    @Test
    void mappedColorReturnsMappedBiome() {
        Holder<Biome> plains = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x112233, plains)),
            (blockX, blockZ) -> sample(0x112233),
            (blockX, blockZ, ecoregionColorRgb) -> 70
        );

        Holder<Biome> result = source.getNoiseBiome(QuartPos.fromBlock(10), 0, QuartPos.fromBlock(20), null);
        assertEquals(plains, result);
    }

    @Test
    void unmappedColorFallsBackToOceanAndDeepOceanByDepthAndTemperateSst() {
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping = mapping(Map.of());
        Holder<Biome> expectedDeep = mapping.deepOceanBiome();
        Holder<Biome> expectedOcean = mapping.oceanBiome();

        EcoregionBiomeSource deepOceanSource = newSource(
            mapping,
            (blockX, blockZ) -> sample(0),
            (blockX, blockZ, ecoregionColorRgb) -> EcoregionBiomeSource.DEEP_OCEAN_TERRAIN_Y_THRESHOLD
        );
        EcoregionBiomeSource oceanSource = newSource(
            mapping,
            (blockX, blockZ) -> sample(0),
            (blockX, blockZ, ecoregionColorRgb) -> sourceShelfTerrainY()
        );

        Holder<Biome> deepResult = deepOceanSource.getNoiseBiome(0, 0, 0, null);
        Holder<Biome> oceanResult = oceanSource.getNoiseBiome(0, 0, 0, null);

        assertEquals(expectedDeep, deepResult);
        assertEquals(expectedOcean, oceanResult);
    }

    @Test
    void quartCoordinatesAreSampledAtFourBlockResolution() {
        Holder<Biome> plains = dummyBiomeHolder();
        AtomicInteger sampledBlockX = new AtomicInteger(Integer.MIN_VALUE);
        AtomicInteger sampledBlockZ = new AtomicInteger(Integer.MIN_VALUE);
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0xABCDEF, plains)),
            (blockX, blockZ) -> {
                sampledBlockX.set(blockX);
                sampledBlockZ.set(blockZ);
                return sample(0xABCDEF);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70
        );

        int quartX = 7;
        int quartZ = -9;
        source.getNoiseBiome(quartX, 0, quartZ, null);

        assertEquals(QuartPos.toBlock(quartX), sampledBlockX.get());
        assertEquals(QuartPos.toBlock(quartZ), sampledBlockZ.get());
    }

    @Test
    void repeatedQuartRequestsHitBiomeHotCache() {
        Holder<Biome> plains = dummyBiomeHolder();
        AtomicInteger sampleCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, plains)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70
        );

        int quartX = 12;
        int quartZ = -6;
        source.getNoiseBiome(quartX, 0, quartZ, null);
        source.getNoiseBiome(quartX, 1, quartZ, null);
        source.getNoiseBiome(quartX, 2, quartZ, null);

        assertEquals(1, sampleCalls.get());
    }

    @Test
    void warmShelfUsesWarmOcean() {
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping = mapping(Map.of());
        EcoregionBiomeSource source = newSource(
            mapping,
            (blockX, blockZ) -> sample(0x000000),
            (blockX, blockZ, ecoregionColorRgb) -> sourceShelfTerrainY()
        );

        Holder<Biome> result = source.oceanFallbackForTerrainAndSst(sourceShelfTerrainY(), 28.0);

        assertEquals(mapping.warmOceanBiome(), result);
    }

    @Test
    void coldDeepWaterUsesDeepColdOcean() {
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping = mapping(Map.of());
        EcoregionBiomeSource source = newSource(
            mapping,
            (blockX, blockZ) -> sample(0x112233),
            (blockX, blockZ, ecoregionColorRgb) -> EcoregionBiomeSource.DEEP_OCEAN_TERRAIN_Y_THRESHOLD - 1
        );

        Holder<Biome> result = source.oceanFallbackForTerrainAndSst(
            EcoregionBiomeSource.DEEP_OCEAN_TERRAIN_Y_THRESHOLD - 1,
            5.0
        );

        assertEquals(mapping.deepColdOceanBiome(), result);
    }

    @Test
    void inlandWaterOverridesMappedBiomeToRiver() {
        Holder<Biome> plains = dummyBiomeHolder();
        Holder<Biome> river = dummyBiomeHolder();
        Holder<Biome> frozenRiver = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x42A1FE, plains), river, frozenRiver),
            (blockX, blockZ) -> sample(0x42A1FE),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.RIVER, 63),
            (biome, blockX, blockY, blockZ, reusablePos) -> false
        );

        Holder<Biome> result = source.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(river, result);
    }

    @Test
    void coldMappedBiomeUsesFrozenRiverForInlandWater() {
        Holder<Biome> snowyPlains = dummyBiomeHolder();
        Holder<Biome> river = dummyBiomeHolder();
        Holder<Biome> frozenRiver = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x13579B, snowyPlains), river, frozenRiver),
            (blockX, blockZ) -> sample(0x13579B),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.RIVER, 63),
            (biome, blockX, blockY, blockZ, reusablePos) -> true
        );

        Holder<Biome> result = source.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(frozenRiver, result);
    }

    @Test
    void autoModeUsesPreferredBiomeWhenAvailable() {
        Holder<Biome> preferred = dummyBiomeHolder();
        Holder<Biome> fallback = dummyBiomeHolder();
        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = resolveModeTestMapping(
            BiomeIntegrationMode.AUTO,
            preferred,
            true,
            fallback,
            true
        );
        EcoregionBiomeSource source = newSource(
            resolved,
            (blockX, blockZ) -> sample(MODE_TEST_COLOR),
            (blockX, blockZ, ecoregionColorRgb) -> 70
        );

        Holder<Biome> result = source.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(preferred, result);
    }

    @Test
    void vanillaModeUsesFallbackEvenWhenPreferredIsAvailable() {
        Holder<Biome> preferred = dummyBiomeHolder();
        Holder<Biome> fallback = dummyBiomeHolder();
        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = resolveModeTestMapping(
            BiomeIntegrationMode.VANILLA,
            preferred,
            true,
            fallback,
            true
        );
        EcoregionBiomeSource source = newSource(
            resolved,
            (blockX, blockZ) -> sample(MODE_TEST_COLOR),
            (blockX, blockZ, ecoregionColorRgb) -> 70
        );

        Holder<Biome> result = source.getNoiseBiome(QuartPos.fromBlock(0), 0, QuartPos.fromBlock(0), null);
        assertEquals(fallback, result);
    }

    @Test
    void expandedModeFailsFastWhenPreferredIsMissing() {
        Holder<Biome> preferred = dummyBiomeHolder();
        Holder<Biome> fallback = dummyBiomeHolder();
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> resolveModeTestMapping(
                BiomeIntegrationMode.EXPANDED,
                preferred,
                false,
                fallback,
                true
            )
        );
        assertTrue(exception.getMessage().contains("Unknown preferred biome id"));
    }

    @Test
    void modeResolutionFailsFastWhenFallbackBiomeIsMissing() {
        Holder<Biome> preferred = dummyBiomeHolder();
        Holder<Biome> fallback = dummyBiomeHolder();

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> resolveModeTestMapping(BiomeIntegrationMode.VANILLA, preferred, false, fallback, false)
        );
        assertTrue(exception.getMessage().contains("Unknown fallback biome id"));
    }

    private static int sourceShelfTerrainY() {
        return EarthGenConfig.mapMetersToTerrainY(-100.0);
    }

    private static EcoregionBiomeMappings.ResolvedBiomeMapping resolveModeTestMapping(
        BiomeIntegrationMode mode,
        Holder<Biome> preferredHolder,
        boolean preferredAvailable,
        Holder<Biome> fallbackHolder,
        boolean fallbackAvailable
    ) {
        return EcoregionBiomeMappings.resolveMappings(
            Map.of(
                MODE_TEST_COLOR,
                new EcoregionBiomeMappings.BiomeSelectionIds(
                    ResourceLocation.parse("biomesoplenty:maple_woods"),
                    ResourceLocation.parse("minecraft:plains")
                )
            ),
            mode,
            biomeId -> {
                if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                    if (preferredAvailable) {
                        return preferredHolder;
                    }
                    throw new IllegalStateException("preferred missing");
                }
                if ("minecraft:plains".equals(biomeId.toString())) {
                    if (fallbackAvailable) {
                        return fallbackHolder;
                    }
                    throw new IllegalStateException("fallback missing");
                }
                return dummyBiomeHolder();
            }
        );
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
        return mapping(byColor, river, frozenRiver, ocean, coldOcean, lukewarmOcean, warmOcean, frozenOcean, deepOcean, deepColdOcean, deepLukewarmOcean, deepFrozenOcean);
    }

    private static EcoregionBiomeMappings.ResolvedBiomeMapping mapping(
        Map<Integer, Holder<Biome>> byColor,
        Holder<Biome> river,
        Holder<Biome> frozenRiver
    ) {
        Holder<Biome> ocean = dummyBiomeHolder();
        Holder<Biome> coldOcean = dummyBiomeHolder();
        Holder<Biome> lukewarmOcean = dummyBiomeHolder();
        Holder<Biome> warmOcean = dummyBiomeHolder();
        Holder<Biome> frozenOcean = dummyBiomeHolder();
        Holder<Biome> deepOcean = dummyBiomeHolder();
        Holder<Biome> deepColdOcean = dummyBiomeHolder();
        Holder<Biome> deepLukewarmOcean = dummyBiomeHolder();
        Holder<Biome> deepFrozenOcean = dummyBiomeHolder();
        return mapping(byColor, river, frozenRiver, ocean, coldOcean, lukewarmOcean, warmOcean, frozenOcean, deepOcean, deepColdOcean, deepLukewarmOcean, deepFrozenOcean);
    }

    private static EcoregionBiomeMappings.ResolvedBiomeMapping mapping(
        Map<Integer, Holder<Biome>> byColor,
        Holder<Biome> river,
        Holder<Biome> frozenRiver,
        Holder<Biome> ocean,
        Holder<Biome> coldOcean,
        Holder<Biome> lukewarmOcean,
        Holder<Biome> warmOcean,
        Holder<Biome> frozenOcean,
        Holder<Biome> deepOcean,
        Holder<Biome> deepColdOcean,
        Holder<Biome> deepLukewarmOcean,
        Holder<Biome> deepFrozenOcean
    ) {
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

    private static EcoregionBiomeSource.ColorSample sample(int color) {
        return new EcoregionBiomeSource.ColorSample(
            color,
            EcoregionBiomeSource.FallbackReason.UNMAPPED_COLOR,
            null,
            -1,
            -1
        );
    }

    private static EcoregionBiomeSource newSource(
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping,
        EcoregionBiomeSource.ColorSampler colorSampler,
        EcoregionBiomeSource.TerrainYSampler terrainYSampler
    ) {
        return newSource(
            mapping,
            colorSampler,
            terrainYSampler,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.NONE, EarthGenConfig.MIN_Y),
            (biome, blockX, blockY, blockZ, reusablePos) -> false
        );
    }

    private static EcoregionBiomeSource newSource(
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping,
        EcoregionBiomeSource.ColorSampler colorSampler,
        EcoregionBiomeSource.TerrainYSampler terrainYSampler,
        EcoregionBiomeSource.InlandWaterSampler inlandWaterSampler,
        EcoregionBiomeSource.BiomeColdSampler biomeColdSampler
    ) {
        return new EcoregionBiomeSource(
            mapping,
            new EcoregionBiomeSource.SamplingAdapters(colorSampler, terrainYSampler, inlandWaterSampler, biomeColdSampler),
            new EarthGenerationProfile(
                EarthGenConfig.DEFAULT_ZOOM,
                EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
                EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
            )
        );
    }
}
