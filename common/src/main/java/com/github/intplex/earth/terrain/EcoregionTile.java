package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;

public final class EcoregionTile {
    private final TileKey key;
    private final int[] argb;
    private final int tileSize;

    public EcoregionTile(TileKey key, BufferedImage image) {
        if (
            image.getWidth() != EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE ||
            image.getHeight() != EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE
        ) {
            throw new IllegalArgumentException("Unexpected tile dimensions for " + key + ": " + image.getWidth() + "x" + image.getHeight());
        }
        this.key = key;
        this.tileSize = image.getWidth();
        this.argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    public TileKey key() {
        return key;
    }

    public int sampleColorRgb(int localX, int localY) {
        int packed = argb[localY * tileSize + localX];
        return packed & 0x00FFFFFF;
    }
}
