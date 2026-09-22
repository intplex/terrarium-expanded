package com.github.intplex.earth.terrain;

import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

public final class EarthAirCarverPolicy {
    private EarthAirCarverPolicy() {
    }

    public static boolean shouldKeep(
        Holder<ConfiguredWorldCarver<?>> holder,
        EarthWorldgenToggles toggles
    ) {
        String path = holder.unwrapKey()
            .map(ResourceKey::identifier)
            .map(location -> location.getPath().toLowerCase(Locale.ROOT))
            .orElse(null);
        boolean canyonType = holder.value().worldCarver() instanceof CanyonWorldCarver;
        return allows(classify(path, canyonType), toggles);
    }

    static AirCarverFamily classify(String path, boolean canyonType) {
        if (path != null && (path.equals("cave_extra_underground") || path.contains("extra_underground"))) {
            return AirCarverFamily.EXTRA_UNDERGROUND;
        }
        if (canyonType || (path != null && (path.contains("canyon") || path.contains("ravine")))) {
            return AirCarverFamily.CANYON;
        }
        // AIR carvers without a more specific classification are caves. This is
        // deliberately fail-closed when caves are disabled, including direct
        // (unregistered) and mod-provided configured carvers.
        return AirCarverFamily.CAVE;
    }

    static boolean allows(AirCarverFamily family, EarthWorldgenToggles toggles) {
        return switch (family) {
            case CAVE -> toggles.caves();
            case CANYON -> toggles.canyons();
            case EXTRA_UNDERGROUND -> toggles.extraUnderground();
        };
    }

    enum AirCarverFamily {
        CAVE,
        CANYON,
        EXTRA_UNDERGROUND
    }
}
