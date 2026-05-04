package com.github.intplex.earth.terrain;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record EarthSurfaceCavesDensityFunction(
    DensityFunction initialDensityWithoutJaggedness,
    DensityFunction finalDensity
) implements DensityFunction {
    private static final double AIR_DENSITY = -1.0;
    private static final double SOLID_DENSITY = 1.0;

    public static final KeyDispatchDataCodec<EarthSurfaceCavesDensityFunction> CODEC = KeyDispatchDataCodec.of(
        RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                DensityFunction.HOLDER_HELPER_CODEC
                    .fieldOf("initial_density_without_jaggedness")
                    .forGetter(EarthSurfaceCavesDensityFunction::initialDensityWithoutJaggedness),
                DensityFunction.HOLDER_HELPER_CODEC
                    .fieldOf("final_density")
                    .forGetter(EarthSurfaceCavesDensityFunction::finalDensity)
            )
            .apply(instance, EarthSurfaceCavesDensityFunction::new)
        )
    );

    public EarthSurfaceCavesDensityFunction {
        initialDensityWithoutJaggedness = Objects.requireNonNull(
            initialDensityWithoutJaggedness,
            "initialDensityWithoutJaggedness"
        );
        finalDensity = Objects.requireNonNull(finalDensity, "finalDensity");
    }

    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        int solidTopY = TerrainService.effectiveSolidTopYAtXZ(functionContext.blockX(), functionContext.blockZ());
        EarthWorldgenToggles toggles = TerrainServices.requireContext().profile().worldgenToggles();
        if (functionContext.blockY() > solidTopY || !toggles.caves()) {
            return computeDensity(functionContext.blockY(), solidTopY, toggles.caves(), 0.0, 0.0);
        }

        double vanillaInitialDensity = initialDensityWithoutJaggedness.compute(functionContext);
        if (vanillaInitialDensity <= 0.0) {
            return computeDensity(functionContext.blockY(), solidTopY, true, vanillaInitialDensity, 0.0);
        }
        return computeDensity(
            functionContext.blockY(),
            solidTopY,
            true,
            vanillaInitialDensity,
            finalDensity.compute(functionContext)
        );
    }

    static double computeDensity(
        int blockY,
        int solidTopY,
        boolean cavesEnabled,
        double vanillaInitialDensity,
        double vanillaFinalDensity
    ) {
        if (blockY > solidTopY) {
            return AIR_DENSITY;
        }
        if (!cavesEnabled) {
            return SOLID_DENSITY;
        }
        if (vanillaInitialDensity <= 0.0) {
            return SOLID_DENSITY;
        }
        return vanillaFinalDensity < 0.0 ? vanillaFinalDensity : SOLID_DENSITY;
    }

    @Override
    public void fillArray(double[] densities, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(densities, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(
            new EarthSurfaceCavesDensityFunction(
                initialDensityWithoutJaggedness.mapAll(visitor),
                finalDensity.mapAll(visitor)
            )
        );
    }

    @Override
    public double minValue() {
        return Math.min(AIR_DENSITY, finalDensity.minValue());
    }

    @Override
    public double maxValue() {
        return SOLID_DENSITY;
    }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
