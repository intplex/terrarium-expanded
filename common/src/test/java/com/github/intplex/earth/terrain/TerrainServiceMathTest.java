package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainServiceMathTest {
    @AfterEach
    void restoreTerrainDefaults() {
        EarthGenConfig.setActiveMaxTerrainY(EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y);
        EarthGenConfig.resetActiveTerrainProfile();
        TerrainService.syncCaveBiomeDepthProfile(CaveBiomeDepthProfile.VANILLA_FALLBACK);
    }

    @Test
    void depthFromTerrainYUsesExpectedAnchors() {
        assertEquals(0.0, TerrainService.depthFromTerrainY(EarthGenConfig.SEA_LEVEL), 1.0e-12);
        assertEquals(-1.0, TerrainService.depthFromTerrainY(EarthGenConfig.MIN_TERRAIN_Y), 1.0e-12);
        assertEquals(1.0, TerrainService.depthFromTerrainY(EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y), 1.0e-12);
    }

    @Test
    void depthFromTerrainYIsMonotonicAcrossTerrainRange() {
        double last = TerrainService.depthFromTerrainY(EarthGenConfig.MIN_TERRAIN_Y);
        for (int y = EarthGenConfig.MIN_TERRAIN_Y + 1; y <= EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y; y++) {
            double current = TerrainService.depthFromTerrainY(y);
            assertTrue(current >= last, "Depth should be non-decreasing as terrainY increases");
            last = current;
        }
    }

    @Test
    void depthFromTerrainYUsesDynamicTerrainCeiling() {
        EarthGenConfig.setActiveMaxTerrainY(511);
        assertEquals(1.0, TerrainService.depthFromTerrainY(511), 1.0e-12);
    }

    @Test
    void biomeDepthAtYUsesSurfaceRelativeAnchors() {
        assertEquals(0.0, TerrainService.biomeDepthAtY(80, 80), 1.0e-12);
        assertEquals(0.0, TerrainService.biomeDepthAtY(96, 80), 1.0e-12);
        assertEquals(1.1, TerrainService.biomeDepthAtY(EarthGenConfig.MIN_Y, 80), 1.0e-12);
        assertEquals(1.1, TerrainService.biomeDepthAtY(-40, 80), 1.0e-12);
        assertEquals(0.55, TerrainService.biomeDepthAtY(8, 80), 1.0e-12);
    }

    @Test
    void caveBiomeDepthProfileDerivesAnchorsFromTaggedParameters() {
        Holder<Biome> surface = dummyBiomeHolder();
        Holder<Biome> cave = dummyBiomeHolder();
        Climate.ParameterList<Holder<Biome>> parameters = new Climate.ParameterList<>(
            List.of(
                Pair.of(parameterPointWithDepth(Climate.Parameter.point(0.0F)), surface),
                Pair.of(parameterPointWithDepth(Climate.Parameter.span(0.25F, 0.85F)), cave),
                Pair.of(parameterPointWithDepth(Climate.Parameter.point(1.2F)), cave)
            )
        );

        CaveBiomeDepthProfile profile = CaveBiomeDepthProfile.fromParameterList(parameters, biome -> biome == cave);
        TerrainService.syncCaveBiomeDepthProfile(profile);

        assertEquals(0.25, profile.undergroundMinDepth(), 1.0e-5);
        assertEquals(0.85, profile.bottomPlateauStartDepth(), 1.0e-5);
        assertEquals(1.2, profile.bottomDepth(), 1.0e-5);
        assertEquals(1.2, TerrainService.biomeDepthAtY(-40, 80), 1.0e-5);
    }

    @Test
    void continentalnessFromReliefTracksRelativeHeight() {
        double low = TerrainMetricsKernel.continentalnessFromRelief(80, 80, 120);
        double mid = TerrainMetricsKernel.continentalnessFromRelief(100, 80, 120);
        double high = TerrainMetricsKernel.continentalnessFromRelief(120, 80, 120);

        assertTrue(low < 0.0);
        assertEquals(0.0, mid, 1.0e-12);
        assertTrue(high > 0.0);
    }

    @Test
    void erosionFromSteepnessIsClampedToRange() {
        assertEquals(-1.0, TerrainMetricsKernel.erosionFromSteepness(0.0), 1.0e-12);
        assertEquals(1.0, TerrainMetricsKernel.erosionFromSteepness(10.0), 1.0e-12);
    }

    @Test
    void snapshotBudgetUsesRuntimeConfigDefaults() {
        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.defaults();
        assertTrue(config.snapshotBudgetBytes() > 0L);
        assertEquals(config.totalBudgetBytes(), config.tileBudgetBytes() + config.snapshotBudgetBytes());
    }

    @Test
    void suppressIsolatedPositiveSpikeFlattensIsolatedOutlier() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            100,
            103,
            8,
            0
        );

        assertEquals(103, filtered);
    }

    @Test
    void suppressIsolatedPositiveSpikeKeepsSupportedMountainPeak() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            116,
            126,
            8,
            2
        );

        assertEquals(130, filtered);
    }

    @Test
    void suppressIsolatedPositiveSpikeKeepsTerrainWhenLocalReliefIsAlreadyHigh() {
        int filtered = TerrainMetricsKernel.suppressIsolatedPositiveSpike(
            130,
            108,
            126,
            8,
            0
        );

        assertEquals(130, filtered);
    }

    private static Climate.ParameterPoint parameterPointWithDepth(Climate.Parameter depth) {
        Climate.Parameter fullRange = Climate.Parameter.span(-1.0F, 1.0F);
        return Climate.parameters(fullRange, fullRange, fullRange, fullRange, depth, fullRange, 0.0F);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Holder<Biome> dummyBiomeHolder() {
        return (Holder<Biome>) (Holder) Holder.direct(new Object());
    }
}
