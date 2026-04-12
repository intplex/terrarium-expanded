package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;

public final class SurfaceWaterTile {
    private final TileKey key;
    private final int[] argb;

    public SurfaceWaterTile(TileKey key, BufferedImage image) {
        if (image.getWidth() != EarthGenConfig.TILE_SIZE || image.getHeight() != EarthGenConfig.TILE_SIZE) {
            throw new IllegalArgumentException("Unexpected tile dimensions for " + key + ": " + image.getWidth() + "x" + image.getHeight());
        }
        this.key = key;
        this.argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    public TileKey key() {
        return key;
    }

    public int sampleArgb(int localX, int localY) {
        return argb[localY * EarthGenConfig.TILE_SIZE + localX];
    }

    public int seasonalityMonthsAt(int localX, int localY) {
        return SurfaceWaterClassifier.seasonalityMonths(sampleArgb(localX, localY));
    }

    public boolean isWaterAt(int localX, int localY, int minWaterMonths) {
        return SurfaceWaterClassifier.isWater(sampleArgb(localX, localY), minWaterMonths);
    }
}
