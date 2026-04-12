package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileServiceZoomTest {
    @Test
    void terrariumAndWaterUrisUseSelectedZoom() {
        assertEquals(
            "https://elevation-tiles-prod.s3.amazonaws.com/terrarium/10/12/34.png",
            TerrariumTileService.buildTileUri(new TileKey(12, 34), 10).toString()
        );
        assertEquals(
            "https://storage.googleapis.com/global-surface-water/tiles2021/seasonality/11/12/34.png",
            SurfaceWaterTileService.buildTileUri(new TileKey(12, 34), 11).toString()
        );
    }

    @Test
    void customBaseUrisAreSupportedAndNormalized() {
        assertEquals(
            "https://example.com/terrain/9/1/2.png",
            TerrariumTileService.buildTileUri("https://example.com/terrain/", new TileKey(1, 2), 9).toString()
        );
        assertEquals(
            "https://example.com/water/9/1/2.png",
            SurfaceWaterTileService.buildTileUri("https://example.com/water/", new TileKey(1, 2), 9).toString()
        );
    }

    @Test
    void ecoregionUriRemainsPinnedToSourceZoomEight() {
        assertEquals(
            "https://d127t6piqu53ls.cloudfront.net/tiles-reduced/8/12/34.png",
            EcoregionTileService.buildTileUri(new TileKey(12, 34)).toString()
        );
        assertEquals(
            "https://example.com/biomes/8/12/34.png",
            EcoregionTileService.buildTileUri("https://example.com/biomes/", new TileKey(12, 34)).toString()
        );
    }

    @Test
    void earthTileValidationUsesActiveZoomGridSize() {
        int originalZoom = EarthGenConfig.activeZoom();
        try {
            EarthGenConfig.setActiveZoom(10);
            int max = EarthGenConfig.tileCountPerAxis(10) - 1;
            assertTrue(RemotePngTileStore.isValidEarthTile(new TileKey(max, max)));
            assertFalse(RemotePngTileStore.isValidEarthTile(new TileKey(max + 1, max)));
        } finally {
            EarthGenConfig.setActiveZoom(originalZoom);
        }
    }

    @Test
    void earthTileValidationSupportsExplicitZoomBounds() {
        int maxAt10 = EarthGenConfig.tileCountPerAxis(10) - 1;
        assertTrue(RemotePngTileStore.isValidEarthTile(new TileKey(maxAt10, maxAt10), 10));
        assertFalse(RemotePngTileStore.isValidEarthTile(new TileKey(maxAt10 + 1, maxAt10), 10));
    }
}
