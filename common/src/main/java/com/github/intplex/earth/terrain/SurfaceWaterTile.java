package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;

public final class SurfaceWaterTile implements WeightedCacheValue {
    private final TileKey key;
    private final byte[] seasonalityMonths;

    public SurfaceWaterTile(TileKey key, BufferedImage image) {
        if (image.getWidth() != EarthGenConfig.TILE_SIZE || image.getHeight() != EarthGenConfig.TILE_SIZE) {
            throw new IllegalArgumentException("Unexpected tile dimensions for " + key + ": " + image.getWidth() + "x" + image.getHeight());
        }
        this.key = key;
        int[] packed = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        this.seasonalityMonths = new byte[packed.length];
        for (int i = 0; i < packed.length; i++) {
            seasonalityMonths[i] = (byte) SurfaceWaterClassifier.seasonalityMonths(packed[i]);
        }
    }

    public TileKey key() {
        return key;
    }

    public int seasonalityMonthsAt(int localX, int localY) {
        return seasonalityMonths[localY * EarthGenConfig.TILE_SIZE + localX] & 0xFF;
    }

    public boolean isWaterAt(int localX, int localY, int minWaterMonths) {
        int threshold = Math.max(1, Math.min(12, minWaterMonths));
        return seasonalityMonthsAt(localX, localY) >= threshold;
    }

    @Override
    public int estimatedBytes() {
        return seasonalityMonths.length;
    }
}
