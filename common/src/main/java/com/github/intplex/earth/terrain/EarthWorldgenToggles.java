package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record EarthWorldgenToggles(
    boolean caves,
    boolean canyons,
    boolean extraUnderground,
    boolean aquifers,
    boolean lavaAquifers,
    boolean villages
) {
    private static final EarthWorldgenToggles DEFAULT = new EarthWorldgenToggles(
        false,
        false,
        false,
        false,
        false,
        false
    );

    public static final MapCodec<EarthWorldgenToggles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
        .group(
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("caves", DEFAULT.caves()).forGetter(EarthWorldgenToggles::caves),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("canyons", DEFAULT.canyons()).forGetter(EarthWorldgenToggles::canyons),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("extra_underground", DEFAULT.extraUnderground()).forGetter(EarthWorldgenToggles::extraUnderground),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("aquifers", DEFAULT.aquifers()).forGetter(EarthWorldgenToggles::aquifers),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("lava_aquifers", DEFAULT.lavaAquifers()).forGetter(EarthWorldgenToggles::lavaAquifers),
            com.mojang.serialization.Codec.BOOL.optionalFieldOf("villages", DEFAULT.villages()).forGetter(EarthWorldgenToggles::villages)
        )
        .apply(
            instance,
            EarthWorldgenToggles::new
        ));

    public static EarthWorldgenToggles defaults() {
        return DEFAULT;
    }

    public boolean allowsStructure(ResourceLocation structureId) {
        String path = structureId.getPath();
        if (path.startsWith("village_")) {
            return villages;
        }
        return true;
    }
}
