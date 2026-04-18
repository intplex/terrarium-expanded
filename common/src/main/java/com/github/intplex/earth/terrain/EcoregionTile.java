package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public final class EcoregionTile implements WeightedCacheValue {
    private final TileKey key;
    private final int[] palette;
    private final byte[] indices8;
    private final short[] indices16;
    private final int[] indices32;
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
        int[] packed = image.getRGB(0, 0, tileSize, tileSize, null, 0, tileSize);
        Map<Integer, Integer> paletteIndexByColor = new HashMap<>();
        int[] paletteScratch = new int[Math.min(256, packed.length)];
        int paletteSize = 0;

        int[] colorIndices = new int[packed.length];
        for (int i = 0; i < packed.length; i++) {
            int colorRgb = packed[i] & 0x00FFFFFF;
            Integer knownIndex = paletteIndexByColor.get(colorRgb);
            if (knownIndex == null) {
                knownIndex = paletteSize;
                paletteIndexByColor.put(colorRgb, knownIndex);
                if (paletteSize == paletteScratch.length) {
                    int[] grown = new int[paletteScratch.length * 2];
                    System.arraycopy(paletteScratch, 0, grown, 0, paletteScratch.length);
                    paletteScratch = grown;
                }
                paletteScratch[paletteSize] = colorRgb;
                paletteSize++;
            }
            colorIndices[i] = knownIndex;
        }

        this.palette = new int[paletteSize];
        System.arraycopy(paletteScratch, 0, palette, 0, paletteSize);
        if (paletteSize <= 256) {
            byte[] indices = new byte[colorIndices.length];
            for (int i = 0; i < colorIndices.length; i++) {
                indices[i] = (byte) colorIndices[i];
            }
            this.indices8 = indices;
            this.indices16 = null;
            this.indices32 = null;
        } else if (paletteSize <= 65_536) {
            short[] indices = new short[colorIndices.length];
            for (int i = 0; i < colorIndices.length; i++) {
                indices[i] = (short) colorIndices[i];
            }
            this.indices8 = null;
            this.indices16 = indices;
            this.indices32 = null;
        } else {
            this.indices8 = null;
            this.indices16 = null;
            this.indices32 = colorIndices;
        }
    }

    public TileKey key() {
        return key;
    }

    public int sampleColorRgb(int localX, int localY) {
        int position = localY * tileSize + localX;
        int paletteIndex;
        if (indices8 != null) {
            paletteIndex = indices8[position] & 0xFF;
        } else if (indices16 != null) {
            paletteIndex = indices16[position] & 0xFFFF;
        } else {
            paletteIndex = indices32[position];
        }
        return palette[paletteIndex];
    }

    @Override
    public int estimatedBytes() {
        int indexBytes = indices8 != null
            ? indices8.length
            : (indices16 != null ? indices16.length * Short.BYTES : indices32.length * Integer.BYTES);
        return palette.length * Integer.BYTES + indexBytes;
    }
}
