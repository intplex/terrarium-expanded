package com.github.intplex.earth.terrain;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TerrainHeightModes(TerrainHeightMode belowSea, TerrainHeightMode aboveSea) {
    public static final TerrainHeightModes EVEN_SCALE = new TerrainHeightModes(TerrainHeightMode.EVEN_SCALE, TerrainHeightMode.EVEN_SCALE);
    public static final MapCodec<TerrainHeightModes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
        .group(
            TerrainHeightMode.CODEC
                .optionalFieldOf("below_sea_height_mode", TerrainHeightMode.EVEN_SCALE)
                .forGetter(TerrainHeightModes::belowSea),
            TerrainHeightMode.CODEC
                .optionalFieldOf("above_sea_height_mode", TerrainHeightMode.EVEN_SCALE)
                .forGetter(TerrainHeightModes::aboveSea)
        )
        .apply(instance, TerrainHeightModes::new));

    public TerrainHeightModes {
        belowSea = TerrainHeightMode.normalize(belowSea);
        aboveSea = TerrainHeightMode.normalize(aboveSea);
    }
}
