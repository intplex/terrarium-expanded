package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

public record CaveBiomeDepthProfile(
    double undergroundMinDepth,
    double bottomPlateauStartDepth,
    double bottomDepth
) {
    private static final double EPSILON = 1.0e-6;
    private static final double FALLBACK_UNDERGROUND_MIN_DEPTH = 0.2;
    private static final double FALLBACK_BOTTOM_PLATEAU_START_DEPTH = 0.9;
    private static final double FALLBACK_BOTTOM_DEPTH = 1.1;
    public static final CaveBiomeDepthProfile VANILLA_FALLBACK = new CaveBiomeDepthProfile(
        FALLBACK_UNDERGROUND_MIN_DEPTH,
        FALLBACK_BOTTOM_PLATEAU_START_DEPTH,
        FALLBACK_BOTTOM_DEPTH
    );

    public CaveBiomeDepthProfile {
        undergroundMinDepth = finiteOrDefault(undergroundMinDepth, FALLBACK_UNDERGROUND_MIN_DEPTH);
        bottomDepth = finiteOrDefault(bottomDepth, FALLBACK_BOTTOM_DEPTH);
        bottomPlateauStartDepth = finiteOrDefault(bottomPlateauStartDepth, bottomDepth);

        undergroundMinDepth = Math.max(0.0, undergroundMinDepth);
        bottomDepth = Math.max(undergroundMinDepth, bottomDepth);
        bottomPlateauStartDepth = Math.max(undergroundMinDepth, Math.min(bottomPlateauStartDepth, bottomDepth));
    }

    public static CaveBiomeDepthProfile fromParameterList(
        Climate.ParameterList<Holder<Biome>> parameters,
        Predicate<Holder<Biome>> caveBiomePredicate
    ) {
        if (parameters == null || caveBiomePredicate == null) {
            return VANILLA_FALLBACK;
        }

        List<Double> maxDepths = new ArrayList<>();
        double minDepth = Double.POSITIVE_INFINITY;
        double bottomDepth = Double.NEGATIVE_INFINITY;

        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : parameters.values()) {
            Holder<Biome> biome = entry.getSecond();
            if (!caveBiomePredicate.test(biome)) {
                continue;
            }

            Climate.Parameter depth = entry.getFirst().depth();
            double rangeMin = Climate.unquantizeCoord(depth.min());
            double rangeMax = Climate.unquantizeCoord(depth.max());
            if (!Double.isFinite(rangeMin) || !Double.isFinite(rangeMax)) {
                continue;
            }

            minDepth = Math.min(minDepth, rangeMin);
            bottomDepth = Math.max(bottomDepth, rangeMax);
            maxDepths.add(rangeMax);
        }

        if (maxDepths.isEmpty() || !Double.isFinite(minDepth) || !Double.isFinite(bottomDepth)) {
            return VANILLA_FALLBACK;
        }

        double plateauStartDepth = bottomDepth;
        for (double maxDepth : maxDepths) {
            if (maxDepth < bottomDepth - EPSILON) {
                plateauStartDepth = Math.max(plateauStartDepth == bottomDepth ? maxDepth : plateauStartDepth, maxDepth);
            }
        }

        return new CaveBiomeDepthProfile(minDepth, plateauStartDepth, bottomDepth);
    }

    public double sampleDepth(int blockY, int solidTopY) {
        if (solidTopY <= TerrainService.OUT_OF_BOUNDS_SOLID_TOP_Y || blockY >= solidTopY) {
            return 0.0;
        }
        double verticalSpan = Math.max(1.0, solidTopY - EarthGenConfig.MIN_Y);
        double belowSurface = solidTopY - blockY;
        double relativeDepth = Math.max(0.0, Math.min(1.0, belowSurface / verticalSpan));
        double mappedDepth = relativeDepth * bottomDepth;
        return mappedDepth >= bottomPlateauStartDepth ? bottomDepth : mappedDepth;
    }

    public boolean isUndergroundBiomeDepth(int blockY, int solidTopY) {
        return sampleDepth(blockY, solidTopY) >= undergroundMinDepth;
    }

    private static double finiteOrDefault(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
