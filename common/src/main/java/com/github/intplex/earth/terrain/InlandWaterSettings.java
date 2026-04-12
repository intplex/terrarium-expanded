package com.github.intplex.earth.terrain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record InlandWaterSettings(boolean enabled, int minWaterMonths) {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MIN_WATER_MONTHS = 10;
    public static final String ENABLED_PROPERTY = "terrarium_expanded.inland_water.enabled";
    public static final String MIN_WATER_MONTHS_PROPERTY = "terrarium_expanded.inland_water.min_water_months";

    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");

    public InlandWaterSettings {
        minWaterMonths = Math.max(1, Math.min(12, minWaterMonths));
    }

    public static InlandWaterSettings loadFromSystemProperties() {
        return new InlandWaterSettings(
            parseBoolean(ENABLED_PROPERTY, DEFAULT_ENABLED),
            parseInt(MIN_WATER_MONTHS_PROPERTY, DEFAULT_MIN_WATER_MONTHS)
        );
    }

    private static boolean parseBoolean(String propertyName, boolean defaultValue) {
        String rawValue = System.getProperty(propertyName);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(rawValue.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(rawValue.trim())) {
            return false;
        }
        LOGGER.warn("Ignoring invalid boolean {} for {}; using default {}", rawValue, propertyName, defaultValue);
        return defaultValue;
    }

    private static int parseInt(String propertyName, int defaultValue) {
        String rawValue = System.getProperty(propertyName);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid integer {} for {}; using default {}", rawValue, propertyName, defaultValue);
            return defaultValue;
        }
    }
}
