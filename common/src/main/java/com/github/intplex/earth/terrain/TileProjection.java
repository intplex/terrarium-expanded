package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.Objects;

final class TileProjection {
    private TileProjection() {
    }

    static boolean projectTerrain(int blockX, int blockZ, int zoom, MutablePoint out) {
        int validatedZoom = EarthGenConfig.validateZoom(zoom);
        return projectInternal(blockX, blockZ, validatedZoom, validatedZoom, EarthGenConfig.TILE_SIZE, out);
    }

    static boolean projectSurfaceWater(int blockX, int blockZ, int zoom, MutablePoint out) {
        return projectTerrain(blockX, blockZ, zoom, out);
    }

    static boolean projectEcoregion(int blockX, int blockZ, int zoom, MutablePoint out) {
        int validatedZoom = EarthGenConfig.validateZoom(zoom);
        return projectInternal(
            blockX,
            blockZ,
            validatedZoom,
            EarthGenConfig.ECOREGION_SOURCE_ZOOM,
            EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE,
            out
        );
    }

    private static boolean projectInternal(
        int blockX,
        int blockZ,
        int worldZoom,
        int sourceZoom,
        int tileSize,
        MutablePoint out
    ) {
        Objects.requireNonNull(out, "out");
        if (sourceZoom > worldZoom) {
            throw new IllegalArgumentException("Source zoom " + sourceZoom + " cannot exceed world zoom " + worldZoom);
        }
        int halfSpan = EarthGenConfig.halfSpanForZoom(worldZoom);
        int span = EarthGenConfig.blockSpanForZoom(worldZoom);
        int globalPxX = blockX + halfSpan;
        int globalPxY = blockZ + halfSpan;
        if (globalPxX < 0 || globalPxX >= span || globalPxY < 0 || globalPxY >= span) {
            return false;
        }

        int scaleShift = worldZoom - sourceZoom;
        int sourceGlobalX = globalPxX >> scaleShift;
        int sourceGlobalY = globalPxY >> scaleShift;

        int tileX = Math.floorDiv(sourceGlobalX, tileSize);
        int tileY = Math.floorDiv(sourceGlobalY, tileSize);
        int localX = Math.floorMod(sourceGlobalX, tileSize);
        int localY = Math.floorMod(sourceGlobalY, tileSize);
        out.set(tileX, tileY, localX, localY);
        return true;
    }

    static final class MutablePoint {
        private int tileX;
        private int tileY;
        private int pixelX;
        private int pixelY;
        private TileKey tileKey = new TileKey(0, 0);

        int pixelX() {
            return pixelX;
        }

        int pixelY() {
            return pixelY;
        }

        TileKey tileKey() {
            return tileKey;
        }

        private void set(int tileX, int tileY, int pixelX, int pixelY) {
            if (this.tileX != tileX || this.tileY != tileY) {
                this.tileKey = new TileKey(tileX, tileY);
            }
            this.tileX = tileX;
            this.tileY = tileY;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
        }
    }
}
