package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;

public final class EarthSamplingResult {
    private EarthSamplingResult() {
    }

    public record TerrainProbe(
        boolean inBounds,
        int terrainY,
        boolean ocean,
        boolean rawSurfaceWater,
        boolean surfaceWaterDataAvailable
    ) {
        public static final TerrainProbe OUT_OF_BOUNDS =
            new TerrainProbe(false, EarthGenConfig.MIN_Y, false, false, true);
    }

    public enum EcoregionStatus {
        SAMPLED,
        OUT_OF_BOUNDS,
        TILE_LOAD_FAILURE
    }

    public record EcoregionProbe(
        EcoregionStatus status,
        int colorRgb,
        TileKey tileKey,
        int pixelX,
        int pixelY,
        String error
    ) {
        static final EcoregionProbe OUT_OF_BOUNDS =
            new EcoregionProbe(EcoregionStatus.OUT_OF_BOUNDS, 0, null, -1, -1, null);

        static EcoregionProbe outOfBounds() {
            return OUT_OF_BOUNDS;
        }
    }

    public enum SurfaceWaterStatus {
        AVAILABLE,
        OUT_OF_BOUNDS,
        OUTSIDE_COVERAGE,
        MISSING,
        FAILED,
        NOT_REQUESTED
    }

    public record SurfaceWaterProbe(
        SurfaceWaterStatus status,
        boolean isWater,
        boolean dataAvailable,
        TileKey tileKey,
        int pixelX,
        int pixelY,
        String error
    ) {
        static final SurfaceWaterProbe OUT_OF_BOUNDS =
            new SurfaceWaterProbe(SurfaceWaterStatus.OUT_OF_BOUNDS, false, false, null, -1, -1, null);
        static final SurfaceWaterProbe NOT_REQUESTED =
            new SurfaceWaterProbe(SurfaceWaterStatus.NOT_REQUESTED, false, true, null, -1, -1, null);
    }
}
