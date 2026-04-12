package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record WeirdnessDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<WeirdnessDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new WeirdnessDensityFunction()));

    /**
     * Returns the Terrarium-derived weirdness channel in {@code [-1, 1]}.
     * Positive values indicate the center column is higher than its local average;
     * negative values indicate it is lower than its local average.
     */
    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        return TerrainService.weirdnessAtXZ(functionContext);
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
