package com.github.intplex.earth.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

public record InlandWaterSettings(boolean enabled, int minWaterMonths) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MIN_WATER_MONTHS = 10;
    public static final InlandWaterSettings DEFAULT = new InlandWaterSettings(DEFAULT_ENABLED, DEFAULT_MIN_WATER_MONTHS);
    public static final Codec<InlandWaterSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.optionalFieldOf("enabled", DEFAULT_ENABLED).forGetter(InlandWaterSettings::enabled),
        Codec.intRange(1, 12).optionalFieldOf("min_water_months", DEFAULT_MIN_WATER_MONTHS)
            .forGetter(InlandWaterSettings::minWaterMonths)
    ).apply(instance, InlandWaterSettings::new));

    // Resolve missing legacy values at decode time, not codec initialization time.
    // Always write the object back, even for defaults, so subsequent loads cannot
    // pick up different machine-local properties.
    public static final MapCodec<InlandWaterSettings> WORLDGEN_CODEC = CODEC.optionalFieldOf("inland_water")
        .xmap(value -> value.orElseGet(() -> loadFromRuntimeConfig(TerrainServices.runtimeConfig())), Optional::of);

    public InlandWaterSettings {
        if (minWaterMonths < 1 || minWaterMonths > 12) {
            throw new IllegalArgumentException("inland_water.min_water_months must be between 1 and 12");
        }
    }

    static InlandWaterSettings loadFromRuntimeConfig(TerrariumRuntimeConfig runtimeConfig) {
        TerrariumRuntimeConfig.InlandWaterConfig config = runtimeConfig.inlandWater();
        return new InlandWaterSettings(config.enabled(), config.minWaterMonths());
    }
}
