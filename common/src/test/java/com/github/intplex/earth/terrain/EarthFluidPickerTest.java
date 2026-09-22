package com.github.intplex.earth.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthFluidPickerTest {
    private static final int SEA_LEVEL = 63;
    private static final Aquifer.FluidPicker LAVA_DELEGATE = (x, y, z) ->
        new Aquifer.FluidStatus(SEA_LEVEL, Blocks.LAVA.defaultBlockState());

    @Test
    void dryModeFillsCaveVoidsBelowOceanColumns() {
        Aquifer.FluidPicker picker = EarthFluidPicker.create(
            LAVA_DELEGATE,
            SEA_LEVEL,
            Blocks.WATER.defaultBlockState(),
            true,
            (x, z) -> 10
        );

        assertTrue(picker.computeFluid(0, 9, 0).at(9).is(Blocks.WATER));
        assertTrue(picker.computeFluid(0, 40, 0).at(40).is(Blocks.WATER));
        assertTrue(picker.computeFluid(0, 62, 0).at(62).is(Blocks.WATER));
    }

    @Test
    void dryModeKeepsUndergroundLandCavesDry() {
        Aquifer.FluidPicker picker = EarthFluidPicker.create(
            LAVA_DELEGATE,
            SEA_LEVEL,
            Blocks.WATER.defaultBlockState(),
            true,
            (x, z) -> 80
        );

        assertTrue(picker.computeFluid(0, 40, 0).at(40).isAir());
    }

    @Test
    void shorelineIsNotMistakenForAColumnWithOceanAboveIt() {
        assertTrue(EarthFluidPicker.isSubmergedColumn(61, SEA_LEVEL));
        assertTrue(!EarthFluidPicker.isSubmergedColumn(62, SEA_LEVEL));
    }

    @Test
    void aquiferModeStillDelegatesBelowSolidTerrain() {
        Aquifer.FluidPicker picker = EarthFluidPicker.create(
            LAVA_DELEGATE,
            SEA_LEVEL,
            Blocks.WATER.defaultBlockState(),
            false,
            (x, z) -> 80
        );

        assertTrue(picker.computeFluid(0, 40, 0).at(40).is(Blocks.LAVA));
    }
}
