package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;

final class TerrainChunkSnapshot implements WeightedCacheValue {
    private static final WaterBodyKind[] WATER_KIND_VALUES = WaterBodyKind.values();
    private final int chunkMinX;
    private final int chunkMinZ;
    private final long[] inBoundsBits;
    private final short[] rawTerrainY;
    private final short[] effectiveSolidTopY;
    private final short[] waterSurfaceY;
    private final long[] oceanMaskBits;
    private final long[] inlandMaskBits;
    private final byte[] waterKindOrdinals;
    private final float[] continentalness;
    private final float[] erosion;
    private final float[] weirdness;

    TerrainChunkSnapshot(
        int chunkMinX,
        int chunkMinZ,
        long[] inBoundsBits,
        short[] rawTerrainY,
        short[] effectiveSolidTopY,
        short[] waterSurfaceY,
        long[] oceanMaskBits,
        long[] inlandMaskBits,
        byte[] waterKindOrdinals,
        float[] continentalness,
        float[] erosion,
        float[] weirdness
    ) {
        this.chunkMinX = chunkMinX;
        this.chunkMinZ = chunkMinZ;
        this.inBoundsBits = inBoundsBits;
        this.rawTerrainY = rawTerrainY;
        this.effectiveSolidTopY = effectiveSolidTopY;
        this.waterSurfaceY = waterSurfaceY;
        this.oceanMaskBits = oceanMaskBits;
        this.inlandMaskBits = inlandMaskBits;
        this.waterKindOrdinals = waterKindOrdinals;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.weirdness = weirdness;
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
        int ordinal = waterKindOrdinals[index] & 0xFF;
        if (ordinal < 0 || ordinal >= WATER_KIND_VALUES.length) {
            return WaterBodyKind.NONE;
        }
        return WATER_KIND_VALUES[ordinal];
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
        return (float) TerrainService.depthFromTerrainY(resolveRawTerrainY(blockX, blockZ));
    }

    boolean oceanAt(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return false;
        }
        return getBit(oceanMaskBits, index);
    }

    boolean inlandAt(int blockX, int blockZ) {
        int index = localIndexOrNegative(blockX, blockZ);
        if (index < 0) {
            return false;
        }
        return getBit(inlandMaskBits, index);
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
        return getBit(inBoundsBits, index) ? index : -1;
    }

    private static int localIndex(int localX, int localZ) {
        return localX * TerrainService.CHUNK_WIDTH + localZ;
    }

    static void setBit(long[] bits, int index, boolean value) {
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        if (value) {
            bits[word] |= mask;
        } else {
            bits[word] &= ~mask;
        }
    }

    static boolean getBit(long[] bits, int index) {
        int word = index >>> 6;
        long mask = 1L << (index & 63);
        return (bits[word] & mask) != 0L;
    }

    @Override
    public int estimatedBytes() {
        return inBoundsBits.length * Long.BYTES
            + rawTerrainY.length * Short.BYTES
            + effectiveSolidTopY.length * Short.BYTES
            + waterSurfaceY.length * Short.BYTES
            + oceanMaskBits.length * Long.BYTES
            + inlandMaskBits.length * Long.BYTES
            + waterKindOrdinals.length
            + continentalness.length * Float.BYTES
            + erosion.length * Float.BYTES
            + weirdness.length * Float.BYTES;
    }
}
