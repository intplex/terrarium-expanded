package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;

public final class TerrariumSeamPatch {
    private static final int SEAM_PATCH_MIN_ZOOM = 11;
    private static final int ZOOM12_EDGE_WIDTH = 2;
    private static final int ZOOM11_EDGE_WIDTH = 1;

    private TerrariumSeamPatch() {
    }

    public static int patchedPixelX(int zoom, TileKey tileKey, int pixelX) {
        if (zoom < SEAM_PATCH_MIN_ZOOM) {
            return pixelX;
        }

        int seamTileX = EarthGenConfig.tileCountPerAxis(zoom) / 2;
        int edgeWidth = zoom >= 12 ? ZOOM12_EDGE_WIDTH : ZOOM11_EDGE_WIDTH;
        if (tileKey.x() == seamTileX - 1 && pixelX >= EarthGenConfig.TILE_SIZE - edgeWidth) {
            return EarthGenConfig.TILE_SIZE - edgeWidth - 1;
        }
        if (tileKey.x() == seamTileX && pixelX < edgeWidth) {
            return edgeWidth;
        }
        return pixelX;
    }
}
