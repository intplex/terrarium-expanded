package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;

final class TerrainChunkSnapshot {
    private final int chunkMinX;
    private final int chunkMinZ;
    private final boolean[] inBounds;
    private final short[] rawTerrainY;
    private final short[] effectiveSolidTopY;
    private final short[] waterSurfaceY;
    private final boolean[] oceanMask;
    private final boolean[] inlandMask;
    private final WaterBodyKind[] waterKind;
    private final float[] continentalness;
    private final float[] erosion;
    private final float[] weirdness;
    private final float[] depth;

    TerrainChunkSnapshot(
        int chunkMinX,
        int chunkMinZ,
        boolean[] inBounds,
        short[] rawTerrainY,
        short[] effectiveSolidTopY,
        short[] waterSurfaceY,
        boolean[] oceanMask,
        boolean[] inlandMask,
        WaterBodyKind[] waterKind,
        float[] continentalness,
        float[] erosion,
        float[] weirdness,
        float[] depth
    ) {
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
        this.inBounds = inBounds;
        this.rawTerrainY = rawTerrainY;
        this.effectiveSolidTopY = effectiveSolidTopY;
        this.waterSurfaceY = waterSurfaceY;
        this.oceanMask = oceanMask;
        this.inlandMask = inlandMask;
        this.waterKind = waterKind;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.weirdness = weirdness;
        this.depth = depth;
    }

    boolean containsBlock(int blockX, int blockZ) {
        return localIndexOrNegative(blockX, blockZ) >= 0;
    }

    int resolveRawTerrainY(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return TerrainService.OUT_OF_BOUNDS_SOLID_TOP_Y;
        }
        return rawTerrainY[index];
    }

    int resolveEffectiveSolidTopY(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return TerrainService.OUT_OF_BOUNDS_SOLID_TOP_Y;
        }
        return effectiveSolidTopY[index];
    }

    int resolveWaterSurfaceY(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return EarthGenConfig.MIN_Y;
        }
        return waterSurfaceY[index];
    }

    WaterBodyKind resolveWaterKind(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return WaterBodyKind.NONE;
        }
        return waterKind[index];
    }

    float resolveContinentalness(int blockX, int blockZ) {
        return continentalness[localIndexUnchecked(blockX, blockZ)];
    }

    float resolveErosion(int blockX, int blockZ) {
        return erosion[localIndexUnchecked(blockX, blockZ)];
    }

    float resolveWeirdness(int blockX, int blockZ) {
        return weirdness[localIndexUnchecked(blockX, blockZ)];
    }

    float resolveDepth(int blockX, int blockZ) {
        return depth[localIndexUnchecked(blockX, blockZ)];
    }

    boolean oceanAt(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return false;
        }
        return oceanMask[index];
    }

    boolean inlandAt(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return false;
        }
        return inlandMask[index];
    }

    private int localIndexUnchecked(int blockX, int blockZ) {
        return localIndex(blockX - chunkMinX, blockZ - chunkMinZ);
    }

    private int localIndexOrNegative(int blockX, int blockZ) {
        int localX = blockX - chunkMinX;
        int localZ = blockZ - chunkMinZ;
        if (localX < 0 || localX >= TerrainService.CHUNK_WIDTH || localZ < 0 || localZ >= TerrainService.CHUNK_WIDTH) {
            return -1;
        }
        int index = localIndex(localX, localZ);
        return inBounds[index] ? index : -1;
    }

    private static int localIndex(int localX, int localZ) {
        return localX * TerrainService.CHUNK_WIDTH + localZ;
    }
}
