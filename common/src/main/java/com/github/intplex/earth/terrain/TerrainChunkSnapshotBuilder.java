package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.Arrays;

final class TerrainChunkSnapshotBuilder {
    private static final int SAMPLE_GRID_MARGIN = TerrainMetricsKernel.RELIEF_RADIUS_BLOCKS;
    private static final int SAMPLE_GRID_SIZE = TerrainService.CHUNK_WIDTH + SAMPLE_GRID_MARGIN * 2;
    private static final int WATER_ANALYSIS_START = 0;
    private static final int WATER_ANALYSIS_END_EXCLUSIVE = SAMPLE_GRID_SIZE;
    private static final ThreadLocal<ScratchBuffers> SCRATCH_BUFFERS = ThreadLocal.withInitial(ScratchBuffers::new);

    private TerrainChunkSnapshotBuilder() {
    }

    static TerrainChunkSnapshot build(int chunkMinX, int chunkMinZ, TerrainService.RuntimeState runtimeState, EarthRuntimeContext context) {
        int chunkCellCount = TerrainService.CHUNK_WIDTH * TerrainService.CHUNK_WIDTH;
        int chunkBitWords = (chunkCellCount + Long.SIZE - 1) / Long.SIZE;

        long[] inBoundsBits = new long[chunkBitWords];
        short[] rawTerrainY = new short[chunkCellCount];
        short[] effectiveSolidTopY = new short[chunkCellCount];
        short[] waterSurfaceY = new short[chunkCellCount];
        long[] oceanMaskBits = new long[chunkBitWords];
        long[] inlandMaskBits = new long[chunkBitWords];
        byte[] waterKindOrdinals = new byte[chunkCellCount];
        float[] continentalness = new float[chunkCellCount];
        float[] erosion = new float[chunkCellCount];
        float[] weirdness = new float[chunkCellCount];

        ScratchBuffers scratch = SCRATCH_BUFFERS.get();
        boolean[] sampleInBounds = scratch.sampleInBounds;
        int[] sampleTerrainY = scratch.sampleTerrainY;
        boolean[] sampleOceanMask = scratch.sampleOceanMask;
        boolean[] sampleRawWaterMask = scratch.sampleRawWaterMask;
        EarthSamplingFacade.MutableTerrainProbe sampleProbe = scratch.sampleProbe;

        EarthSamplingFacade.LocalTileCaches tileCaches = EarthSamplingFacade.chunkLocalCaches();

        boolean waterDataComplete = true;
        for (int sampleX = 0; sampleX < SAMPLE_GRID_SIZE; sampleX++) {
            int blockX = chunkMinX + sampleX - SAMPLE_GRID_MARGIN;
            for (int sampleZ = 0; sampleZ < SAMPLE_GRID_SIZE; sampleZ++) {
                int blockZ = chunkMinZ + sampleZ - SAMPLE_GRID_MARGIN;
                int sampleIndex = sampleIndex(sampleX, sampleZ);
                EarthSamplingFacade.sampleTerrainInto(context, blockX, blockZ, tileCaches, sampleProbe);
                sampleInBounds[sampleIndex] = sampleProbe.inBounds();
                sampleTerrainY[sampleIndex] = sampleProbe.terrainY();
                sampleOceanMask[sampleIndex] = sampleProbe.ocean();
                sampleRawWaterMask[sampleIndex] = sampleProbe.rawSurfaceWater();
                if (sampleProbe.inBounds() && runtimeState.inlandWaterSettings().enabled() && !sampleProbe.surfaceWaterDataAvailable()) {
                    waterDataComplete = false;
                }
            }
        }

        TerrainMetricsKernel.suppressIsolatedSpikes(sampleInBounds, sampleTerrainY, SAMPLE_GRID_SIZE, SAMPLE_GRID_SIZE);

        InlandWaterAnalysis.FlatResult waterResult = runtimeState.inlandWaterSettings().enabled() && !waterDataComplete
            ? noInlandWaterResultFlat(sampleInBounds, sampleTerrainY, scratch)
            : InlandWaterAnalysis.analyzeFlat(
                sampleInBounds,
                sampleTerrainY,
                sampleOceanMask,
                sampleRawWaterMask,
                runtimeState.inlandWaterSettings(),
                SAMPLE_GRID_SIZE,
                WATER_ANALYSIS_START,
                WATER_ANALYSIS_START,
                WATER_ANALYSIS_END_EXCLUSIVE,
                WATER_ANALYSIS_END_EXCLUSIVE
            );

        for (int localX = 0; localX < TerrainService.CHUNK_WIDTH; localX++) {
            int centerSampleX = localX + SAMPLE_GRID_MARGIN;
            for (int localZ = 0; localZ < TerrainService.CHUNK_WIDTH; localZ++) {
                int centerSampleZ = localZ + SAMPLE_GRID_MARGIN;
                int centerSampleIndex = sampleIndex(centerSampleX, centerSampleZ);
                int localIndex = localIndex(localX, localZ);

                boolean centerInBounds = sampleInBounds[centerSampleIndex];
                int centerY = sampleTerrainY[centerSampleIndex];

                TerrainChunkSnapshot.setBit(inBoundsBits, localIndex, centerInBounds);
                rawTerrainY[localIndex] = (short) (centerInBounds ? centerY : EarthGenConfig.MIN_Y);
                effectiveSolidTopY[localIndex] = (short) (centerInBounds ? waterResult.effectiveSolidTopY()[centerSampleIndex] : EarthGenConfig.MIN_Y);
                waterSurfaceY[localIndex] = (short) waterResult.waterSurfaceY()[centerSampleIndex];
                TerrainChunkSnapshot.setBit(oceanMaskBits, localIndex, sampleOceanMask[centerSampleIndex]);
                TerrainChunkSnapshot.setBit(inlandMaskBits, localIndex, waterResult.inlandMask()[centerSampleIndex]);
                waterKindOrdinals[localIndex] = (byte) waterResult.waterKind()[centerSampleIndex].ordinal();

                if (!centerInBounds) {
                    continentalness[localIndex] = -1.0f;
                    erosion[localIndex] = -1.0f;
                    weirdness[localIndex] = 0.0f;
                    continue;
                }

                TerrainService.ColumnMetrics metrics = TerrainMetricsKernel.computeMetricsAt(
                    centerSampleX,
                    centerSampleZ,
                    centerY,
                    sampleInBounds,
                    sampleTerrainY,
                    SAMPLE_GRID_SIZE
                );
                continentalness[localIndex] = metrics.continentalness();
                erosion[localIndex] = metrics.erosion();
                weirdness[localIndex] = metrics.weirdness();
            }
        }

        return new TerrainChunkSnapshot(
            chunkMinX,
            chunkMinZ,
            inBoundsBits,
            rawTerrainY,
            effectiveSolidTopY,
            waterSurfaceY,
            oceanMaskBits,
            inlandMaskBits,
            waterKindOrdinals,
            continentalness,
            erosion,
            weirdness
        );
    }

    private static InlandWaterAnalysis.FlatResult noInlandWaterResultFlat(boolean[] inBounds, int[] terrainY, ScratchBuffers scratch) {
        int size = terrainY.length;
        boolean[] inlandMask = scratch.noInlandMask;
        WaterBodyKind[] waterKind = scratch.noInlandWaterKind;
        int[] waterSurfaceY = scratch.noInlandWaterSurfaceY;
        int[] effectiveSolidTopY = scratch.noInlandEffectiveSolidTopY;
        Arrays.fill(inlandMask, false);
        for (int i = 0; i < size; i++) {
            waterKind[i] = WaterBodyKind.NONE;
            waterSurfaceY[i] = EarthGenConfig.MIN_Y;
            effectiveSolidTopY[i] = inBounds[i] ? terrainY[i] : EarthGenConfig.MIN_Y;
        }
        return new InlandWaterAnalysis.FlatResult(inlandMask, waterKind, waterSurfaceY, effectiveSolidTopY, SAMPLE_GRID_SIZE);
    }

    private static int sampleIndex(int x, int z) {
        return x * SAMPLE_GRID_SIZE + z;
    }

    private static int localIndex(int x, int z) {
        return x * TerrainService.CHUNK_WIDTH + z;
    }

    private static final class ScratchBuffers {
        private final boolean[] sampleInBounds = new boolean[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final int[] sampleTerrainY = new int[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final boolean[] sampleOceanMask = new boolean[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final boolean[] sampleRawWaterMask = new boolean[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final boolean[] noInlandMask = new boolean[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final WaterBodyKind[] noInlandWaterKind = new WaterBodyKind[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final int[] noInlandWaterSurfaceY = new int[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final int[] noInlandEffectiveSolidTopY = new int[SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE];
        private final EarthSamplingFacade.MutableTerrainProbe sampleProbe = new EarthSamplingFacade.MutableTerrainProbe();
    }
}
