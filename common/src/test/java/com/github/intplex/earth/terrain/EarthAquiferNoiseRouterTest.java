package com.github.intplex.earth.terrain;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EarthAquiferNoiseRouterTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void waterOnlyModeDisablesOnlyLateAquiferLavaConversion() {
        DensityFunction shared = DensityFunctions.constant(0.25);
        DensityFunction lava = DensityFunctions.constant(0.75);
        NoiseRouter source = new NoiseRouter(
            shared,
            shared,
            shared,
            lava,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared
        );

        NoiseRouter adjusted = EarthAquiferNoiseRouter.withoutLava(source);

        assertEquals(
            0.0,
            adjusted.lavaNoise().compute(new DensityFunction.SinglePointContext(0, -32, 0)),
            1.0e-12
        );
        assertSame(source.barrierNoise(), adjusted.barrierNoise());
        assertSame(source.fluidLevelFloodednessNoise(), adjusted.fluidLevelFloodednessNoise());
        assertSame(source.fluidLevelSpreadNoise(), adjusted.fluidLevelSpreadNoise());
        assertSame(source.finalDensity(), adjusted.finalDensity());
        assertSame(source.veinToggle(), adjusted.veinToggle());
    }

    @Test
    void lavaSuppressionIsScopedToTheEarthNoiseChunkConstruction() {
        DensityFunction shared = DensityFunctions.constant(0.25);
        NoiseRouter source = new NoiseRouter(
            shared,
            shared,
            shared,
            DensityFunctions.constant(0.75),
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared,
            shared
        );

        NoiseRouter adjusted = EarthAquiferNoiseRouter.withLavaDisabled(
            () -> EarthAquiferNoiseRouter.forCurrentAquifer(source)
        );

        assertEquals(
            0.0,
            adjusted.lavaNoise().compute(new DensityFunction.SinglePointContext(0, -32, 0)),
            1.0e-12
        );
        assertSame(source, EarthAquiferNoiseRouter.forCurrentAquifer(source));
    }
}
