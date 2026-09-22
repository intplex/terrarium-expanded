package com.github.intplex.earth.terrain;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;

public final class EarthAquiferNoiseRouter {
    private static final ThreadLocal<Boolean> LAVA_DISABLED = new ThreadLocal<>();

    private EarthAquiferNoiseRouter() {
    }

    /**
     * Disables the noise aquifer's late water-to-lava conversion while leaving
     * every terrain, biome, ore, and water-level density function unchanged.
     */
    public static NoiseRouter withoutLava(NoiseRouter source) {
        Objects.requireNonNull(source, "source");
        return new NoiseRouter(
            source.barrierNoise(),
            source.fluidLevelFloodednessNoise(),
            source.fluidLevelSpreadNoise(),
            DensityFunctions.zero(),
            source.temperature(),
            source.vegetation(),
            source.continents(),
            source.erosion(),
            source.depth(),
            source.ridges(),
            source.preliminarySurfaceLevel(),
            source.finalDensity(),
            source.veinToggle(),
            source.veinRidged(),
            source.veinGap()
        );
    }

    public static NoiseRouter forCurrentAquifer(NoiseRouter source) {
        return Boolean.TRUE.equals(LAVA_DISABLED.get()) ? withoutLava(source) : source;
    }

    public static <T> T withLavaDisabled(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        Boolean previous = LAVA_DISABLED.get();
        LAVA_DISABLED.set(true);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                LAVA_DISABLED.remove();
            } else {
                LAVA_DISABLED.set(previous);
            }
        }
    }
}
