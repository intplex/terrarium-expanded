package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;

public final class TerrariumTile {
    private final TileKey key;
    private final int[] argb;

    public TerrariumTile(TileKey key, BufferedImage image) {
        if (image.getWidth() != EarthGenConfig.TILE_SIZE || image.getHeight() != EarthGenConfig.TILE_SIZE) {
            throw new IllegalArgumentException("Unexpected tile dimensions for " + key + ": " + image.getWidth() + "x" + image.getHeight());
        }
        this.key = key;
        this.argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    public TileKey key() {
        return key;
    }

    public double sampleMeters(int localX, int localY) {
        int packed = argb[localY * EarthGenConfig.TILE_SIZE + localX];
        int red = (packed >>> 16) & 0xFF;
        int green = (packed >>> 8) & 0xFF;
        int blue = packed & 0xFF;
        return EarthGenConfig.decodeTerrariumMeters(red, green, blue);
    }
}
