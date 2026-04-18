package com.github.intplex.earth.terrain;

public record InlandWaterSettings(boolean enabled, int minWaterMonths) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MIN_WATER_MONTHS = 10;

    public InlandWaterSettings {
        minWaterMonths = Math.max(1, Math.min(12, minWaterMonths));
    }

    static InlandWaterSettings loadFromRuntimeConfig(TerrariumRuntimeConfig runtimeConfig) {
        TerrariumRuntimeConfig.InlandWaterConfig config = runtimeConfig.inlandWater();
        return new InlandWaterSettings(config.enabled(), config.minWaterMonths());
    }
}
