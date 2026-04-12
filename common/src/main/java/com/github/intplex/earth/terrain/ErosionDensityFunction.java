package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record ErosionDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<ErosionDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new ErosionDensityFunction()));

    /**
     * Returns the Terrarium-derived erosion channel in {@code [-1, 1]} based on
     * local slope magnitude. Flatter regions trend negative; steeper regions trend
     * positive.
     */
    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        return TerrainService.erosionAtXZ(functionContext);
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
