package com.github.intplex.earth.terrain;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum TerrainHeightMode implements StringRepresentable {
    EVEN_SCALE("even_scale"),
    SEA_LEVEL_DETAIL("sea_level_detail"),
    HIGH_ELEVATION_DETAIL("high_elevation_detail"),
    COMPRESSED_MIDDLE_HEIGHTS("compressed_middle_heights");

    public static final Codec<TerrainHeightMode> CODEC = StringRepresentable.fromEnum(TerrainHeightMode::values);

    private final String serializedName;

    TerrainHeightMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static TerrainHeightMode normalize(TerrainHeightMode mode) {
        return mode == null ? EVEN_SCALE : mode;
    }

    public static TerrainHeightMode fromSerializedName(String serializedName) {
        String normalized = serializedName == null ? "" : serializedName.trim().toLowerCase(Locale.ROOT);
        for (TerrainHeightMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported terrain height mode " + serializedName);
    }

    public double apply(double t) {
        double clamped = clamp01(t);
        double mapped = switch (this) {
            case EVEN_SCALE -> clamped;
            case SEA_LEVEL_DETAIL -> 1.0 - Math.pow(1.0 - clamped, 2.0);
            case HIGH_ELEVATION_DETAIL -> clamped * clamped;
            case COMPRESSED_MIDDLE_HEIGHTS -> clamped + (0.5 * Math.sin(2.0 * Math.PI * clamped) / (2.0 * Math.PI));
        };
        return clamp01(mapped);
    }

    private static double clamp01(double value) {
        if (value <= 0.0) {
            return 0.0;
        }
        if (value >= 1.0) {
            return 1.0;
        }
        return value;
    }
}
