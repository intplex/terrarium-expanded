package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public record EnvelopeDensityFunction() implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<EnvelopeDensityFunction> CODEC =
            KeyDispatchDataCodec.of(MapCodec.unit(new EnvelopeDensityFunction()));

    /**
     * Produces a binary density envelope from Terrarium height data at this column.
     * {@code 1.0} means this Y is inside solid terrain; {@code -1.0} means this Y
     * is above terrain and should remain air.
     */
    @Override
    public double compute(DensityFunction.FunctionContext functionContext) {
        return TerrainService.envelopeDensityAtXYZ(functionContext);
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
