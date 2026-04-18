package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionTileServiceTest {
    @Test
    void reducedTileValidatorUses64By64GridBounds() {
        int max = EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS - 1;
        assertTrue(EcoregionTileService.isValidReducedTileKey(new TileKey(0, 0)));
        assertTrue(EcoregionTileService.isValidReducedTileKey(new TileKey(max, max)));
        assertFalse(EcoregionTileService.isValidReducedTileKey(new TileKey(-1, 0)));
        assertFalse(EcoregionTileService.isValidReducedTileKey(new TileKey(0, -1)));
        assertFalse(EcoregionTileService.isValidReducedTileKey(new TileKey(max + 1, 0)));
        assertFalse(EcoregionTileService.isValidReducedTileKey(new TileKey(0, max + 1)));
    }

    @Test
    void uriBuilderTargetsReducedPath() {
        URI uri = EcoregionTileService.buildTileUri(new TileKey(12, 34));
        assertEquals("https://d127t6piqu53ls.cloudfront.net/tiles-reduced/8/12/34.png", uri.toString());
        URI customUri = EcoregionTileService.buildTileUri("https://example.com/biomes/", new TileKey(12, 34));
        assertEquals("https://example.com/biomes/8/12/34.png", customUri.toString());
    }

    @Test
    void defaultMemoryCacheIsReducedForLargerTiles() {
        assertEquals(TerrariumRuntimeConfig.DEFAULT_ECOREGION_TILE_CONFIG.cacheEntries(), EcoregionTileService.DEFAULT_MEMORY_CACHE_ENTRIES);
    }
}
