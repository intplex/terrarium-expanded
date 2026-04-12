package com.github.intplex.earth.biome;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.WaterBodyKind;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EcoregionBiomeSourceZoomTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void defaultsToZoomEightWhenNotProvided() {
        EcoregionBiomeSource source = new EcoregionBiomeSource(
            mapping(Map.of()),
            EcoregionBiomeSource.SamplingAdapters.defaults(),
            defaultProfile()
        );
        assertEquals(EarthGenConfig.DEFAULT_ZOOM, source.zoom());
        assertEquals(EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y, source.maxMountainY());
        assertEquals(EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y, source.oceanFloorY());
        assertEquals(BiomeIntegrationMode.AUTO, source.biomeIntegration());
    }

    @Test
    void acceptsExplicitZoom() {
        EcoregionBiomeSource source = new EcoregionBiomeSource(
            mapping(Map.of()),
            EcoregionBiomeSource.SamplingAdapters.defaults(),
            new EarthGenerationProfile(11, EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y, EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y),
            BiomeIntegrationMode.EXPANDED
        );
        assertEquals(11, source.zoom());
        assertEquals(BiomeIntegrationMode.EXPANDED, source.biomeIntegration());
    }

    @Test
    void acceptsExplicitTerrainProfileY() {
        EcoregionBiomeSource source = new EcoregionBiomeSource(
            mapping(Map.of()),
            new EcoregionBiomeSource.SamplingAdapters(
                (blockX, blockZ) -> new EcoregionBiomeSource.ColorSample(0, EcoregionBiomeSource.FallbackReason.UNMAPPED_COLOR, null, -1, -1),
                (blockX, blockZ, ecoregionColorRgb) -> 63,
                (blockX, blockZ) -> new EcoregionBiomeSource.InlandWaterSample(WaterBodyKind.NONE, EarthGenConfig.MIN_Y),
                (biome, blockX, blockY, blockZ, reusablePos) -> false
            ),
            new EarthGenerationProfile(11, 180, 20)
        );
        assertEquals(11, source.zoom());
        assertEquals(180, source.maxMountainY());
        assertEquals(20, source.oceanFloorY());
    }

    @Test
    void rejectsUnsupportedZoom() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new EcoregionBiomeSource(mapping(Map.of()), EcoregionBiomeSource.SamplingAdapters.defaults(), new EarthGenerationProfile(7, 180, 20))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new EcoregionBiomeSource(mapping(Map.of()), EcoregionBiomeSource.SamplingAdapters.defaults(), new EarthGenerationProfile(13, 180, 20))
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

    private static EarthGenerationProfile defaultProfile() {
        return new EarthGenerationProfile(
            EarthGenConfig.DEFAULT_ZOOM,
            EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
            EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y
        );
    }
}
