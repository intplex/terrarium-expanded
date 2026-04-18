package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrariumTileTest {
    @Test
    void sampleMetersMatchesTerrariumDecodeFormula() {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00000000);
        image.setRGB(1, 0, 0x00123456);
        image.setRGB(2, 0, 0x00ABCDEF);
        image.setRGB(3, 0, 0x00FFFFFF);
        image.setRGB(20, 40, 0x0080AA20);

        TerrariumTile tile = new TerrariumTile(new TileKey(0, 0), image);
        assertEquals(decodedMeters(0x00000000), tile.sampleMeters(0, 0));
        assertEquals(decodedMeters(0x00123456), tile.sampleMeters(1, 0));
        assertEquals(decodedMeters(0x00ABCDEF), tile.sampleMeters(2, 0));
        assertEquals(decodedMeters(0x00FFFFFF), tile.sampleMeters(3, 0));
        assertEquals(decodedMeters(0x0080AA20), tile.sampleMeters(20, 40));
    }

    @Test
    void estimatedBytesUsesPackedRgbStorage() {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        TerrariumTile tile = new TerrariumTile(new TileKey(1, 2), image);
        assertEquals(EarthGenConfig.TILE_SIZE * EarthGenConfig.TILE_SIZE * 3, tile.estimatedBytes());
    }

    private static double decodedMeters(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return EarthGenConfig.decodeTerrariumMeters(red, green, blue);
    }
}
