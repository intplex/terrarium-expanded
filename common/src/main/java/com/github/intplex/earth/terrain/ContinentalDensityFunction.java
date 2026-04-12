package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record ContinentalDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<ContinentalDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new ContinentalDensityFunction()));

    /**
     * Returns the Terrarium-derived continentalness channel in {@code [-1, 1]}.
     * Lower values represent locally low terrain relative to nearby samples,
     * while higher values represent locally high terrain.
     */
    @Override
    public double compute(FunctionContext functionContext) {
        return TerrainService.continentalnessAtXZ(functionContext);
    }

    @Override
    public double minValue() {
        return -1.0;
    }

    @Override
    public double maxValue() {
        return 1.0;
    }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
