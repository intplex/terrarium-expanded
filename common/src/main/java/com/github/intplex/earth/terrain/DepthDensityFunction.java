package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record DepthDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<DepthDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new DepthDensityFunction()));

    /**
     * Returns a biome-depth channel from this Y position relative to the
     * Terrarium terrain top in the column. Surface points are {@code 0.0};
     * deeper underground points approach vanilla's deep-dark anchor.
     */
    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        return TerrainService.depthDensityAtY(functionContext);
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return 1.1;
    }

    @Override
    public @NotNull KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

}
