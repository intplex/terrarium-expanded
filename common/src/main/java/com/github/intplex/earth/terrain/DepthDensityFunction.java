package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record DepthDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<DepthDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new DepthDensityFunction()));

    /**
     * Returns a precomputed depth channel in {@code [-1, 1]} from Terrarium terrain
     * height at this column.
     */
    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        return TerrainService.depthDensityAtY(functionContext);
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
