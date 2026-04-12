package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrariumSeamPatchTest {
    @Test
    void zoomTenDoesNotPatchAnyPixel() {
        int seamTileX = EarthGenConfig.tileCountPerAxis(10) / 2;
        assertEquals(255, TerrariumSeamPatch.patchedPixelX(10, new TileKey(seamTileX - 1, 0), 255));
        assertEquals(0, TerrariumSeamPatch.patchedPixelX(10, new TileKey(seamTileX, 0), 0));
    }

    @Test
    void zoomElevenPatchesPrimeMeridianEdgePixelsToNearestInterior() {
        int seamTileX = EarthGenConfig.tileCountPerAxis(11) / 2;
        assertEquals(254, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX - 1, 0), 255));
        assertEquals(1, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX, 0), 0));
    }

    @Test
    void zoomElevenLeavesNonSeamPixelsUntouched() {
        int seamTileX = EarthGenConfig.tileCountPerAxis(11) / 2;
        assertEquals(253, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX - 1, 0), 253));
        assertEquals(2, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX, 0), 2));
        assertEquals(255, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX + 1, 0), 255));
        assertEquals(0, TerrariumSeamPatch.patchedPixelX(11, new TileKey(seamTileX - 2, 0), 0));
    }

    @Test
    void zoomTwelvePatchesTwoEdgeColumnsPerSide() {
        int seamTileX = EarthGenConfig.tileCountPerAxis(12) / 2;
        assertEquals(253, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX - 1, 0), 255));
        assertEquals(253, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX - 1, 0), 254));
        assertEquals(2, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX, 0), 0));
        assertEquals(2, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX, 0), 1));
        assertEquals(253, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX - 1, 0), 253));
        assertEquals(2, TerrariumSeamPatch.patchedPixelX(12, new TileKey(seamTileX, 0), 2));
    }
}
