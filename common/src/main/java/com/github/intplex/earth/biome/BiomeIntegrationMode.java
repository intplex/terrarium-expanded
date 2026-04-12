package com.github.intplex.earth.biome;

import com.mojang.serialization.Codec;
import java.util.Locale;

public enum BiomeIntegrationMode {
    AUTO("auto"),
    VANILLA("vanilla"),
    EXPANDED("expanded");

    public static final Codec<BiomeIntegrationMode> CODEC = Codec.STRING.xmap(
        BiomeIntegrationMode::fromSerializedName,
        BiomeIntegrationMode::serializedName
    );

    private final String serializedName;

    BiomeIntegrationMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BiomeIntegrationMode fromSerializedName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("biome_integration must not be null");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (BiomeIntegrationMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
            "Unsupported biome_integration '" + raw + "'; expected one of: auto, vanilla, expanded"
        );
    }
}
