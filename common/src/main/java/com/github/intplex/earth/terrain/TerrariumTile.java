package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;

public final class TerrariumTile implements WeightedCacheValue {
    private static final int BYTES_PER_PIXEL = 3;
    private final TileKey key;
    private final byte[] rgb;

    public TerrariumTile(TileKey key, BufferedImage image) {
        if (image.getWidth() != EarthGenConfig.TILE_SIZE || image.getHeight() != EarthGenConfig.TILE_SIZE) {
            throw new IllegalArgumentException("Unexpected tile dimensions for " + key + ": " + image.getWidth() + "x" + image.getHeight());
        }
        this.key = key;
        int[] packed = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        this.rgb = new byte[packed.length * BYTES_PER_PIXEL];
        for (int i = 0; i < packed.length; i++) {
            int pixel = packed[i];
            int outIndex = i * BYTES_PER_PIXEL;
            rgb[outIndex] = (byte) ((pixel >>> 16) & 0xFF);
            rgb[outIndex + 1] = (byte) ((pixel >>> 8) & 0xFF);
            rgb[outIndex + 2] = (byte) (pixel & 0xFF);
        }
    }

    public TileKey key() {
        return key;
    }

    public double sampleMeters(int localX, int localY) {
        int index = (localY * EarthGenConfig.TILE_SIZE + localX) * BYTES_PER_PIXEL;
        int red = rgb[index] & 0xFF;
        int green = rgb[index + 1] & 0xFF;
        int blue = rgb[index + 2] & 0xFF;
        return EarthGenConfig.decodeTerrariumMeters(red, green, blue);
    }

    @Override
    public int estimatedBytes() {
        return rgb.length;
    }
}
