package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EcoregionTileTest {
    @Test
    void rejectsUnexpectedTileDimensions() {
        BufferedImage invalid = new BufferedImage(EarthGenConfig.TILE_SIZE, EarthGenConfig.TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> new EcoregionTile(new TileKey(0, 0), invalid));
    }

    @Test
    void samplesRgbUsingReducedTileCoordinates() {
        int tileSize = EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE;
        BufferedImage image = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00112233);
        image.setRGB(tileSize - 1, tileSize - 1, 0x00ABCDEF);
        image.setRGB(123, 456, 0x0000AA55);

        EcoregionTile tile = new EcoregionTile(new TileKey(5, 7), image);
        assertEquals(0x112233, tile.sampleColorRgb(0, 0));
        assertEquals(0xABCDEF, tile.sampleColorRgb(tileSize - 1, tileSize - 1));
        assertEquals(0x00AA55, tile.sampleColorRgb(123, 456));
    }
}
