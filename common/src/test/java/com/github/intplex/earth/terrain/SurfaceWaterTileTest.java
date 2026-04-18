package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceWaterTileTest {
    @Test
    void seasonalityAndWaterClassificationMatchClassifier() {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        image.setRGB(1, 0, 0xFF0000AA);
        image.setRGB(2, 0, 0xFF99D9EA);
        image.setRGB(3, 0, 0x00FFFFFF);
        image.setRGB(10, 10, 0xFF4477CC);

        SurfaceWaterTile tile = new SurfaceWaterTile(new TileKey(0, 0), image);
        assertSeasonality(tile, 0, 0, 0xFFFFFFFF);
        assertSeasonality(tile, 1, 0, 0xFF0000AA);
        assertSeasonality(tile, 2, 0, 0xFF99D9EA);
        assertSeasonality(tile, 3, 0, 0x00FFFFFF);
        assertSeasonality(tile, 10, 10, 0xFF4477CC);
    }

    @Test
    void estimatedBytesUsesOneBytePerPixel() {
        BufferedImage image = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        SurfaceWaterTile tile = new SurfaceWaterTile(new TileKey(1, 2), image);
        assertEquals(EarthGenConfig.TILE_SIZE * EarthGenConfig.TILE_SIZE, tile.estimatedBytes());
        assertTrue(tile.estimatedBytes() > 0);
    }

    private static void assertSeasonality(SurfaceWaterTile tile, int x, int y, int argb) {
        int expectedMonths = SurfaceWaterClassifier.seasonalityMonths(argb);
        assertEquals(expectedMonths, tile.seasonalityMonthsAt(x, y));
        assertEquals(SurfaceWaterClassifier.isWater(argb, 10), tile.isWaterAt(x, y, 10));
    }
}
