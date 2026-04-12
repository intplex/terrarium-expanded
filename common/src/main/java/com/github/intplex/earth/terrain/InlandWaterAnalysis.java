package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.util.Arrays;

final class InlandWaterAnalysis {
    private static final int MAX_INTERIOR_DEPTH_BONUS = 6;
    private static final int DEPTH_STEP_DISTANCE_BLOCKS = 2;

    private InlandWaterAnalysis() {
    }

    static Result analyze(
        boolean[][] inBounds,
        int[][] terrainY,
        boolean[][] oceanMask,
        boolean[][] rawWaterMask,
        InlandWaterSettings settings,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        int sizeX = terrainY.length;
        int sizeZ = terrainY[0].length;
        WaterBodyKind[][] waterKind = new WaterBodyKind[sizeX][sizeZ];
        boolean[][] inlandMask = new boolean[sizeX][sizeZ];
        int[][] waterSurfaceY = new int[sizeX][sizeZ];
        int[][] effectiveSolidTopY = new int[sizeX][sizeZ];

        for (int x = 0; x < sizeX; x++) {
            Arrays.fill(waterKind[x], WaterBodyKind.NONE);
            for (int z = 0; z < sizeZ; z++) {
                waterSurfaceY[x][z] = EarthGenConfig.MIN_Y;
                effectiveSolidTopY[x][z] = inBounds[x][z] ? terrainY[x][z] : EarthGenConfig.MIN_Y;
            }
        }

        if (!settings.enabled()) {
            return new Result(inlandMask, waterKind, waterSurfaceY, effectiveSolidTopY);
        }

        boolean[][] inlandCandidates = new boolean[sizeX][sizeZ];
        int[][] shoreDistance = new int[sizeX][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            Arrays.fill(shoreDistance[x], -1);
        }

        int candidateCount = 0;
        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                if (!inBounds[x][z] || oceanMask[x][z] || !rawWaterMask[x][z]) {
                    continue;
                }
                inlandCandidates[x][z] = true;
                candidateCount++;
            }
        }

        int[] queueX = new int[candidateCount];
        int[] queueZ = new int[candidateCount];
        int queueHead = 0;
        int queueTail = 0;

        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                if (!inlandCandidates[x][z]) {
                    continue;
                }
                if (!isShoreCell(inlandCandidates, x, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)) {
                    continue;
                }
                shoreDistance[x][z] = 0;
                queueX[queueTail] = x;
                queueZ[queueTail] = z;
                queueTail++;
            }
        }

        while (queueHead < queueTail) {
            int x = queueX[queueHead];
            int z = queueZ[queueHead];
            queueHead++;
            int nextDistance = shoreDistance[x][z] + 1;

            queueTail = enqueueIfInterior(inlandCandidates, shoreDistance, x + 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queueX, queueZ, queueTail);
            queueTail = enqueueIfInterior(inlandCandidates, shoreDistance, x - 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queueX, queueZ, queueTail);
            queueTail = enqueueIfInterior(inlandCandidates, shoreDistance, x, z + 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queueX, queueZ, queueTail);
            queueTail = enqueueIfInterior(inlandCandidates, shoreDistance, x, z - 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queueX, queueZ, queueTail);
        }

        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                if (!inlandCandidates[x][z]) {
                    continue;
                }

                inlandMask[x][z] = true;
                waterKind[x][z] = WaterBodyKind.RIVER;
                waterSurfaceY[x][z] = terrainY[x][z];
                int distanceFromShore = Math.max(0, shoreDistance[x][z]);
                int depthBonus = Math.min(MAX_INTERIOR_DEPTH_BONUS, distanceFromShore / DEPTH_STEP_DISTANCE_BLOCKS);
                int depthBlocks = 1 + depthBonus;
                effectiveSolidTopY[x][z] = Math.max(EarthGenConfig.MIN_Y, terrainY[x][z] - depthBlocks);
            }
        }

        return new Result(inlandMask, waterKind, waterSurfaceY, effectiveSolidTopY);
    }

    static FlatResult analyzeFlat(
        boolean[] inBounds,
        int[] terrainY,
        boolean[] oceanMask,
        boolean[] rawWaterMask,
        InlandWaterSettings settings,
        int stride,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        int size = terrainY.length;
        WaterBodyKind[] waterKind = new WaterBodyKind[size];
        boolean[] inlandMask = new boolean[size];
        int[] waterSurfaceY = new int[size];
        int[] effectiveSolidTopY = new int[size];

        for (int index = 0; index < size; index++) {
            waterKind[index] = WaterBodyKind.NONE;
            waterSurfaceY[index] = EarthGenConfig.MIN_Y;
            effectiveSolidTopY[index] = inBounds[index] ? terrainY[index] : EarthGenConfig.MIN_Y;
        }

        if (!settings.enabled()) {
            return new FlatResult(inlandMask, waterKind, waterSurfaceY, effectiveSolidTopY, stride);
        }

        boolean[] inlandCandidates = new boolean[size];
        int[] shoreDistance = new int[size];
        Arrays.fill(shoreDistance, -1);

        int candidateCount = 0;
        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                int index = x * stride + z;
                if (!inBounds[index] || oceanMask[index] || !rawWaterMask[index]) {
                    continue;
                }
                inlandCandidates[index] = true;
                candidateCount++;
            }
        }

        int[] queue = new int[candidateCount];
        int queueHead = 0;
        int queueTail = 0;
        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                int index = x * stride + z;
                if (!inlandCandidates[index]) {
                    continue;
                }
                if (!isShoreCellFlat(inlandCandidates, stride, x, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)) {
                    continue;
                }
                shoreDistance[index] = 0;
                queue[queueTail] = index;
                queueTail++;
            }
        }

        while (queueHead < queueTail) {
            int index = queue[queueHead++];
            int x = index / stride;
            int z = index % stride;
            int nextDistance = shoreDistance[index] + 1;

            queueTail = enqueueIfInteriorFlat(inlandCandidates, shoreDistance, stride, x + 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queue, queueTail);
            queueTail = enqueueIfInteriorFlat(inlandCandidates, shoreDistance, stride, x - 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queue, queueTail);
            queueTail = enqueueIfInteriorFlat(inlandCandidates, shoreDistance, stride, x, z + 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queue, queueTail);
            queueTail = enqueueIfInteriorFlat(inlandCandidates, shoreDistance, stride, x, z - 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive, nextDistance, queue, queueTail);
        }

        for (int x = analysisMinX; x < analysisMaxXExclusive; x++) {
            for (int z = analysisMinZ; z < analysisMaxZExclusive; z++) {
                int index = x * stride + z;
                if (!inlandCandidates[index]) {
                    continue;
                }

                inlandMask[index] = true;
                waterKind[index] = WaterBodyKind.RIVER;
                waterSurfaceY[index] = terrainY[index];
                int distanceFromShore = Math.max(0, shoreDistance[index]);
                int depthBonus = Math.min(MAX_INTERIOR_DEPTH_BONUS, distanceFromShore / DEPTH_STEP_DISTANCE_BLOCKS);
                int depthBlocks = 1 + depthBonus;
                effectiveSolidTopY[index] = Math.max(EarthGenConfig.MIN_Y, terrainY[index] - depthBlocks);
            }
        }

        return new FlatResult(inlandMask, waterKind, waterSurfaceY, effectiveSolidTopY, stride);
    }

    record Result(
        boolean[][] inlandMask,
        WaterBodyKind[][] waterKind,
        int[][] waterSurfaceY,
        int[][] effectiveSolidTopY
    ) {
    }

    record FlatResult(
        boolean[] inlandMask,
        WaterBodyKind[] waterKind,
        int[] waterSurfaceY,
        int[] effectiveSolidTopY,
        int stride
    ) {
    }

    private static boolean isShoreCell(
        boolean[][] inlandCandidates,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        return !isInlandNeighbor(inlandCandidates, x + 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighbor(inlandCandidates, x - 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighbor(inlandCandidates, x, z + 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighbor(inlandCandidates, x, z - 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive);
    }

    private static boolean isInlandNeighbor(
        boolean[][] inlandCandidates,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        if (x < analysisMinX || x >= analysisMaxXExclusive || z < analysisMinZ || z >= analysisMaxZExclusive) {
            return false;
        }
        return inlandCandidates[x][z];
    }

    private static int enqueueIfInterior(
        boolean[][] inlandCandidates,
        int[][] shoreDistance,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive,
        int newDistance,
        int[] queueX,
        int[] queueZ,
        int queueTail
    ) {
        if (x < analysisMinX || x >= analysisMaxXExclusive || z < analysisMinZ || z >= analysisMaxZExclusive) {
            return queueTail;
        }
        if (!inlandCandidates[x][z] || shoreDistance[x][z] >= 0) {
            return queueTail;
        }
        shoreDistance[x][z] = newDistance;
        queueX[queueTail] = x;
        queueZ[queueTail] = z;
        return queueTail + 1;
    }

    private static boolean isShoreCellFlat(
        boolean[] inlandCandidates,
        int stride,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        return !isInlandNeighborFlat(inlandCandidates, stride, x + 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighborFlat(inlandCandidates, stride, x - 1, z, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighborFlat(inlandCandidates, stride, x, z + 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive)
            || !isInlandNeighborFlat(inlandCandidates, stride, x, z - 1, analysisMinX, analysisMinZ, analysisMaxXExclusive, analysisMaxZExclusive);
    }

    private static boolean isInlandNeighborFlat(
        boolean[] inlandCandidates,
        int stride,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive
    ) {
        if (x < analysisMinX || x >= analysisMaxXExclusive || z < analysisMinZ || z >= analysisMaxZExclusive) {
            return false;
        }
        return inlandCandidates[x * stride + z];
    }

    private static int enqueueIfInteriorFlat(
        boolean[] inlandCandidates,
        int[] shoreDistance,
        int stride,
        int x,
        int z,
        int analysisMinX,
        int analysisMinZ,
        int analysisMaxXExclusive,
        int analysisMaxZExclusive,
        int newDistance,
        int[] queue,
        int queueTail
    ) {
        if (x < analysisMinX || x >= analysisMaxXExclusive || z < analysisMinZ || z >= analysisMaxZExclusive) {
            return queueTail;
        }
        int index = x * stride + z;
        if (!inlandCandidates[index] || shoreDistance[index] >= 0) {
            return queueTail;
        }
        shoreDistance[index] = newDistance;
        queue[queueTail] = index;
        return queueTail + 1;
    }
}
