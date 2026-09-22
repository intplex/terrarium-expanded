package com.github.intplex.earth.terrain;

import java.util.Objects;
import java.util.function.IntBinaryOperator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;

public final class EarthFluidPicker {
    private EarthFluidPicker() {
    }

    public static Aquifer.FluidPicker create(
        Aquifer.FluidPicker delegate,
        int seaLevel,
        BlockState defaultFluid,
        boolean dryUnderground
    ) {
        return create(
            delegate,
            seaLevel,
            defaultFluid,
            dryUnderground,
            TerrainService::effectiveSolidTopYAtXZ
        );
    }

    static Aquifer.FluidPicker create(
        Aquifer.FluidPicker delegate,
        int seaLevel,
        BlockState defaultFluid,
        boolean dryUnderground,
        IntBinaryOperator solidTopAt
    ) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(defaultFluid, "defaultFluid");
        Objects.requireNonNull(solidTopAt, "solidTopAt");

        Aquifer.FluidStatus airOnly = new Aquifer.FluidStatus(
            Integer.MIN_VALUE,
            Blocks.AIR.defaultBlockState()
        );
        Aquifer.FluidStatus surfaceFluid = new Aquifer.FluidStatus(seaLevel, defaultFluid);
        return (x, y, z) -> {
            int solidTopY = solidTopAt.applyAsInt(x, z);
            if (y > solidTopY) {
                return y < seaLevel ? surfaceFluid : airOnly;
            }
            if (dryUnderground) {
                return isSubmergedColumn(solidTopY, seaLevel) ? surfaceFluid : airOnly;
            }
            return delegate.computeFluid(x, y, z);
        };
    }

    static boolean isSubmergedColumn(int solidTopY, int seaLevel) {
        // FluidStatus fills strictly below seaLevel, so seaLevel - 1 is the
        // highest possible ocean-fluid block. A solid top there is shoreline,
        // while a lower top has ocean water above it.
        return solidTopY < seaLevel - 1;
    }
}
