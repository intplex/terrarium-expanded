package com.github.intplex.earth.biome;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.terrain.CaveBiomeDepthProfile;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import com.github.intplex.earth.terrain.OceanSurfaceTemperatureService;
import com.github.intplex.earth.terrain.WaterBodyKind;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionBiomeSourceTest {
    private static final short[] TEMPERATE_TEST_GRID = new short[180 * 360];
    private static final int MODE_TEST_COLOR = 0xABCDEF;
    private static final Climate.Sampler TEST_SAMPLER = new Climate.Sampler(null, null, null, null, null, null, List.of());
    private static final EarthWorldgenToggles CAVES_ENABLED = new EarthWorldgenToggles(
        true,
        false,
        false,
        false,
        false,
        false
    );

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
        AtomicInteger undergroundDelegateCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, plains)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            nullUndergroundDelegate(undergroundDelegateCalls)
        );

        int quartX = 12;
        int quartZ = -6;
        int nearSurfaceQuartY = QuartPos.fromBlock(70);
        source.getNoiseBiome(quartX, nearSurfaceQuartY, quartZ, TEST_SAMPLER);
        source.getNoiseBiome(quartX, nearSurfaceQuartY + 1, quartZ, TEST_SAMPLER);
        source.getNoiseBiome(quartX, nearSurfaceQuartY + 2, quartZ, TEST_SAMPLER);

        assertEquals(0, undergroundDelegateCalls.get());
        assertEquals(1, sampleCalls.get());
    }

    @Test
    void nearSurfaceBiomeSamplingIgnoresUndergroundDelegate() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        AtomicInteger sampleCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            fixedUndergroundDelegate(underground)
        );

        Holder<Biome> result = source.getNoiseBiome(12, QuartPos.fromBlock(70), -6, TEST_SAMPLER);

        assertEquals(surface, result);
        assertEquals(1, sampleCalls.get());
    }

    @Test
    void deepBiomeSamplingUsesUndergroundDelegateBeforeSurfaceSampling() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        AtomicInteger sampleCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            fixedUndergroundDelegate(underground)
        );

        Holder<Biome> result = source.getNoiseBiome(12, -10, -6, TEST_SAMPLER);

        assertEquals(underground, result);
        assertEquals(0, sampleCalls.get());
    }

    @Test
    void deepBiomeSamplingFallsBackToSurfaceWhenUndergroundDelegateDeclines() {
        Holder<Biome> surface = dummyBiomeHolder();
        AtomicInteger sampleCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            nullUndergroundDelegate(new AtomicInteger())
        );

        Holder<Biome> result = source.getNoiseBiome(12, -10, -6, TEST_SAMPLER);

        assertEquals(surface, result);
        assertEquals(1, sampleCalls.get());
    }

    @Test
    void undergroundCandidateHelperReturnsDelegateBiome() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        AtomicInteger sampleCalls = new AtomicInteger();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> {
                sampleCalls.incrementAndGet();
                return sample(0x55AA11);
            },
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.NONE, EarthGenConfig.MIN_Y),
            (biome, blockX, blockY, blockZ, reusablePos) -> false,
            fixedUndergroundDelegate(underground),
            EarthWorldgenToggles.defaults()
        );

        Holder<Biome> result = source.sampleTaggedUndergroundBiome(12, -10, -6, TEST_SAMPLER);

        assertEquals(underground, result);
        assertEquals(0, sampleCalls.get());
    }

    @Test
    void surfaceRelativeUndergroundGuardRejectsNearTerrainSurface() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> sample(0x55AA11),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            fixedUndergroundDelegate(underground)
        );

        assertFalse(source.isSurfaceRelativeUndergroundCell(12, QuartPos.fromBlock(70), -6));
    }

    @Test
    void undergroundCandidateHelperReturnsNullWhenDelegateDeclines() {
        Holder<Biome> surface = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> sample(0x55AA11),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            nullUndergroundDelegate(new AtomicInteger())
        );

        Holder<Biome> result = source.sampleTaggedUndergroundBiome(12, -10, -6, TEST_SAMPLER);

        assertEquals(null, result);
    }

    @Test
    void possibleBiomesAdvertisesUndergroundBiomesWhenCavesCanGenerate() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> sample(0x55AA11),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            fixedUndergroundDelegate(underground)
        );

        assertTrue(source.possibleBiomes().contains(surface));
        assertTrue(source.possibleBiomes().contains(underground));
    }

    @Test
    void possibleBiomesOmitsUndergroundBiomesWhenCavesCannotGenerate() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> sample(0x55AA11),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.NONE, EarthGenConfig.MIN_Y),
            (biome, blockX, blockY, blockZ, reusablePos) -> false,
            fixedUndergroundDelegate(underground),
            EarthWorldgenToggles.defaults()
        );

        assertTrue(source.possibleBiomes().contains(surface));
        assertFalse(source.possibleBiomes().contains(underground));
    }

    @Test
    void undergroundOnlyLocateDelegatesToUndergroundSource() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> underground = dummyBiomeHolder();
        EcoregionBiomeSource source = newSource(
            mapping(Map.of(0x55AA11, surface)),
            (blockX, blockZ) -> sample(0x55AA11),
            (blockX, blockZ, ecoregionColorRgb) -> 70,
            fixedUndergroundDelegate(underground)
        );

        Pair<BlockPos, Holder<Biome>> result = source.findClosestBiome3d(
            new BlockPos(0, 32, 0),
            64,
            32,
            8,
            biome -> biome == underground,
            TEST_SAMPLER,
            null
        );

        assertEquals(underground, result.getSecond());
    }

    @Test
    void undergroundDelegateToleratesUnboundPresetHolderDuringRegistryDecode() {
        Holder.Reference<MultiNoiseBiomeSourceParameterList> unboundOverworldParameters =
            Holder.Reference.createStandAlone(
                new HolderOwner<>() {},
                MultiNoiseBiomeSourceParameterLists.OVERWORLD
            );

        EcoregionBiomeSource.UndergroundBiomeDelegate delegate =
            EcoregionBiomeSource.createUndergroundBiomeDelegate(new HolderGetter<>() {
                @Override
                public Optional<Holder.Reference<MultiNoiseBiomeSourceParameterList>> get(
                    ResourceKey<MultiNoiseBiomeSourceParameterList> key
                ) {
                    return key.equals(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
                        ? Optional.of(unboundOverworldParameters)
                        : Optional.empty();
                }

                @Override
                public Optional<HolderSet.Named<MultiNoiseBiomeSourceParameterList>> get(
                    TagKey<MultiNoiseBiomeSourceParameterList> tagKey
                ) {
                    return Optional.empty();
                }
            });

        assertEquals(CaveBiomeDepthProfile.VANILLA_FALLBACK, delegate.depthProfile());
        assertEquals(0, delegate.possibleBiomes().count());
        assertEquals(null, delegate.getNoiseBiome(0, 0, 0, TEST_SAMPLER));
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
    void biomesOPlentyModeFailsFastWhenPreferredIsMissing() {
        Holder<Biome> preferred = dummyBiomeHolder();
        Holder<Biome> fallback = dummyBiomeHolder();
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> resolveModeTestMapping(
                BiomeIntegrationMode.BIOMES_O_PLENTY,
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
                    ResourceLocation.parse("minecraft:plains"),
                    Map.of(
                        EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY,
                        new EcoregionBiomeMappings.ProviderBiome(ResourceLocation.parse("biomesoplenty:maple_woods"), 100)
                    )
                )
            ),
            mode,
            Set.of(EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY),
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
        EcoregionBiomeSource.UndergroundBiomeDelegate undergroundBiomeDelegate
    ) {
        return newSource(
            mapping,
            colorSampler,
            terrainYSampler,
            (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.NONE, EarthGenConfig.MIN_Y),
            (biome, blockX, blockY, blockZ, reusablePos) -> false,
            undergroundBiomeDelegate
        );
    }

    private static EcoregionBiomeSource newSource(
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping,
        EcoregionBiomeSource.ColorSampler colorSampler,
        EcoregionBiomeSource.TerrainYSampler terrainYSampler,
        EcoregionBiomeSource.InlandWaterSampler inlandWaterSampler,
        EcoregionBiomeSource.BiomeColdSampler biomeColdSampler
    ) {
        return newSource(mapping, colorSampler, terrainYSampler, inlandWaterSampler, biomeColdSampler, EcoregionBiomeSource.UndergroundBiomeDelegate.NONE);
    }

    private static EcoregionBiomeSource newSource(
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping,
        EcoregionBiomeSource.ColorSampler colorSampler,
        EcoregionBiomeSource.TerrainYSampler terrainYSampler,
        EcoregionBiomeSource.InlandWaterSampler inlandWaterSampler,
        EcoregionBiomeSource.BiomeColdSampler biomeColdSampler,
        EcoregionBiomeSource.UndergroundBiomeDelegate undergroundBiomeDelegate
    ) {
        return newSource(
            mapping,
            colorSampler,
            terrainYSampler,
            inlandWaterSampler,
            biomeColdSampler,
            undergroundBiomeDelegate,
            CAVES_ENABLED
        );
    }

    private static EcoregionBiomeSource newSource(
        EcoregionBiomeMappings.ResolvedBiomeMapping mapping,
        EcoregionBiomeSource.ColorSampler colorSampler,
        EcoregionBiomeSource.TerrainYSampler terrainYSampler,
        EcoregionBiomeSource.InlandWaterSampler inlandWaterSampler,
        EcoregionBiomeSource.BiomeColdSampler biomeColdSampler,
        EcoregionBiomeSource.UndergroundBiomeDelegate undergroundBiomeDelegate,
        EarthWorldgenToggles worldgenToggles
    ) {
        return new EcoregionBiomeSource(
            mapping,
            new EcoregionBiomeSource.SamplingAdapters(colorSampler, terrainYSampler, inlandWaterSampler, biomeColdSampler),
            profile(worldgenToggles),
            BiomeIntegrationMode.AUTO,
            undergroundBiomeDelegate
        );
    }

    private static EarthGenerationProfile profile(EarthWorldgenToggles worldgenToggles) {
        return new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
            EarthGenerationProfile.DEFAULT_TERRAIN_BASE_URL,
            EarthGenerationProfile.DEFAULT_BIOMES_BASE_URL,
            EarthGenerationProfile.DEFAULT_SURFACE_WATER_BASE_URL,
            EarthGenerationProfile.TERRAIN_FIXES_NONE,
            worldgenToggles,
            false
        );
    }

    private static EcoregionBiomeSource.UndergroundBiomeDelegate fixedUndergroundDelegate(Holder<Biome> biome) {
        return new EcoregionBiomeSource.UndergroundBiomeDelegate() {
            @Override
            public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
                return biome;
            }

            @Override
            public java.util.stream.Stream<Holder<Biome>> possibleBiomes() {
                return java.util.stream.Stream.of(biome);
            }

            @Override
            public CaveBiomeDepthProfile depthProfile() {
                return CaveBiomeDepthProfile.VANILLA_FALLBACK;
            }

            @Override
            public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
                BlockPos blockPos,
                int radius,
                int horizontalStep,
                int verticalStep,
                Predicate<Holder<Biome>> predicate,
                Climate.Sampler sampler,
                LevelReader levelReader
            ) {
                return predicate.test(biome) ? Pair.of(blockPos, biome) : null;
            }
        };
    }

    private static EcoregionBiomeSource.UndergroundBiomeDelegate nullUndergroundDelegate(AtomicInteger calls) {
        return new EcoregionBiomeSource.UndergroundBiomeDelegate() {
            @Override
            public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
                calls.incrementAndGet();
                return null;
            }

            @Override
            public java.util.stream.Stream<Holder<Biome>> possibleBiomes() {
                return java.util.stream.Stream.empty();
            }

            @Override
            public CaveBiomeDepthProfile depthProfile() {
                return CaveBiomeDepthProfile.VANILLA_FALLBACK;
            }

            @Override
            public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
                BlockPos blockPos,
                int radius,
                int horizontalStep,
                int verticalStep,
                Predicate<Holder<Biome>> predicate,
                Climate.Sampler sampler,
                LevelReader levelReader
            ) {
                return null;
            }
        };
    }
}
