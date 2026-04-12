package com.github.intplex.earth.terrain;

import java.util.Arrays;

final class TerrainMetricsKernel {
    static final int RELIEF_RADIUS_BLOCKS = 12;
    static final int RELIEF_STEP_BLOCKS = 4;
    static final double RELIEF_SCALE_BLOCKS = 40.0;
    static final int SLOPE_STEP_BLOCKS = 4;
    static final double STEEPNESS_FOR_MAX = 1.2;
    static final int WEIRDNESS_RADIUS_BLOCKS = 6;
    static final double WEIRDNESS_SCALE_BLOCKS = 24.0;
    static final int SPIKE_MIN_RISE_BLOCKS = 4;
    static final int SPIKE_EXTRA_OVER_RELIEF_BLOCKS = 2;
    static final int SPIKE_NEAR_TOP_TOLERANCE_BLOCKS = 2;
    static final int SPIKE_MIN_VALID_NEIGHBORS = 6;
    private static final ReliefKernel RELIEF_KERNEL = ReliefKernel.build();

    private TerrainMetricsKernel() {
    }

    static TerrainService.ColumnMetrics computeMetricsAt(int centerSampleX, int centerSampleZ, int centerY, boolean[] inBounds, int[] terrainY, int stride) {
        int localMin = centerY;
        int localMax = centerY;
        int east = centerY;
        int west = centerY;
        int south = centerY;
        int north = centerY;
        int weirdnessSamples = 0;
        double weirdnessSum = 0.0;

        for (int i = 0; i < RELIEF_KERNEL.count; i++) {
            int sampleX = centerSampleX + RELIEF_KERNEL.dx[i];
            int sampleZ = centerSampleZ + RELIEF_KERNEL.dz[i];
            int sampleIndex = sampleX * stride + sampleZ;
            int sampleY = inBounds[sampleIndex] ? terrainY[sampleIndex] : centerY;
            localMin = Math.min(localMin, sampleY);
            localMax = Math.max(localMax, sampleY);
            if (RELIEF_KERNEL.weirdnessWindow[i]) {
                weirdnessSum += sampleY;
                weirdnessSamples++;
            }
            if (i == RELIEF_KERNEL.eastIndex) {
                east = sampleY;
            } else if (i == RELIEF_KERNEL.westIndex) {
                west = sampleY;
            } else if (i == RELIEF_KERNEL.southIndex) {
                south = sampleY;
            } else if (i == RELIEF_KERNEL.northIndex) {
                north = sampleY;
            }
        }

        double slopeX = (east - west) / (double) (SLOPE_STEP_BLOCKS * 2);
        double slopeZ = (south - north) / (double) (SLOPE_STEP_BLOCKS * 2);
        double gradient = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);
        double localAverage = weirdnessSamples > 0 ? weirdnessSum / weirdnessSamples : centerY;
        double delta = centerY - localAverage;
        return new TerrainService.ColumnMetrics(
            (float) continentalnessFromRelief(centerY, localMin, localMax),
            (float) erosionFromSteepness(gradient),
            (float) clamp(delta / WEIRDNESS_SCALE_BLOCKS),
            (float) TerrainService.depthFromTerrainY(centerY)
        );
    }

    static void suppressIsolatedSpikes(boolean[] inBounds, int[] terrainY, int sampleCountX, int sampleCountZ) {
        int maxCells = sampleCountX * sampleCountZ;
        int[] spikeIndices = new int[Math.min(64, Math.max(1, maxCells))];
        int[] spikeHeights = new int[spikeIndices.length];
        int spikeCount = 0;
        for (int x = 0; x < sampleCountX; x++) {
            for (int z = 0; z < sampleCountZ; z++) {
                int centerIndex = x * sampleCountZ + z;
                if (!inBounds[centerIndex]) {
                    continue;
                }
                int centerY = terrainY[centerIndex];
                int neighborMinY = Integer.MAX_VALUE;
                int neighborMaxY = Integer.MIN_VALUE;
                int validNeighborCount = 0;
                int nearTopNeighborCount = 0;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int neighborX = x + dx;
                        int neighborZ = z + dz;
                        if (neighborX < 0 || neighborX >= sampleCountX || neighborZ < 0 || neighborZ >= sampleCountZ) {
                            continue;
                        }
                        int neighborIndex = neighborX * sampleCountZ + neighborZ;
                        if (!inBounds[neighborIndex]) {
                            continue;
                        }
                        int neighborY = terrainY[neighborIndex];
                        validNeighborCount++;
                        neighborMinY = Math.min(neighborMinY, neighborY);
                        neighborMaxY = Math.max(neighborMaxY, neighborY);
                        if (neighborY >= centerY - SPIKE_NEAR_TOP_TOLERANCE_BLOCKS) {
                            nearTopNeighborCount++;
                        }
                    }
                }

                int suppressedY = suppressIsolatedPositiveSpike(centerY, neighborMinY, neighborMaxY, validNeighborCount, nearTopNeighborCount);
                if (suppressedY != centerY) {
                    if (spikeCount == spikeIndices.length) {
                        int newCapacity = Math.min(maxCells, spikeIndices.length * 2);
                        spikeIndices = Arrays.copyOf(spikeIndices, newCapacity);
                        spikeHeights = Arrays.copyOf(spikeHeights, newCapacity);
                    }
                    spikeIndices[spikeCount] = centerIndex;
                    spikeHeights[spikeCount] = suppressedY;
                    spikeCount++;
                }
            }
        }

        for (int i = 0; i < spikeCount; i++) {
            terrainY[spikeIndices[i]] = spikeHeights[i];
        }
    }

    static int suppressIsolatedPositiveSpike(int centerY, int neighborMinY, int neighborMaxY, int validNeighborCount, int nearTopNeighborCount) {
        if (validNeighborCount < SPIKE_MIN_VALID_NEIGHBORS) {
            return centerY;
        }
        int riseAboveNeighbors = centerY - neighborMaxY;
        if (riseAboveNeighbors < SPIKE_MIN_RISE_BLOCKS) {
            return centerY;
        }
        int neighborRelief = neighborMaxY - neighborMinY;
        if (riseAboveNeighbors < neighborRelief + SPIKE_EXTRA_OVER_RELIEF_BLOCKS || nearTopNeighborCount > 0) {
            return centerY;
        }
        return neighborMaxY;
    }

    static double continentalnessFromRelief(int centerY, int localMinY, int localMaxY) {
        if (localMaxY <= localMinY) {
            return 0.0;
        }
        double normalizedPosition = (centerY - localMinY) / (double) (localMaxY - localMinY);
        double centered = normalizedPosition * 2.0 - 1.0;
        double variationScale = Math.min((localMaxY - localMinY) / RELIEF_SCALE_BLOCKS, 1.0);
        return clamp(centered * variationScale);
    }

    static double erosionFromSteepness(double steepness) {
        double normalized = Math.max(0.0, Math.min(1.0, steepness / STEEPNESS_FOR_MAX));
        return clamp(normalized * 2.0 - 1.0);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private static final class ReliefKernel {
        private final int[] dx;
        private final int[] dz;
        private final boolean[] weirdnessWindow;
        private final int count;
        private final int eastIndex;
        private final int westIndex;
        private final int southIndex;
        private final int northIndex;

        private ReliefKernel(int[] dx, int[] dz, boolean[] weirdnessWindow, int count, int eastIndex, int westIndex, int southIndex, int northIndex) {
            this.dx = dx;
            this.dz = dz;
            this.weirdnessWindow = weirdnessWindow;
            this.count = count;
            this.eastIndex = eastIndex;
            this.westIndex = westIndex;
            this.southIndex = southIndex;
            this.northIndex = northIndex;
        }

        private static ReliefKernel build() {
            int stepsPerAxis = (RELIEF_RADIUS_BLOCKS * 2) / RELIEF_STEP_BLOCKS + 1;
            int sampleCount = stepsPerAxis * stepsPerAxis;
            int[] dx = new int[sampleCount];
            int[] dz = new int[sampleCount];
            boolean[] weirdnessWindow = new boolean[sampleCount];
            int eastIndex = -1;
            int westIndex = -1;
            int southIndex = -1;
            int northIndex = -1;
            int index = 0;

            for (int x = -RELIEF_RADIUS_BLOCKS; x <= RELIEF_RADIUS_BLOCKS; x += RELIEF_STEP_BLOCKS) {
                for (int z = -RELIEF_RADIUS_BLOCKS; z <= RELIEF_RADIUS_BLOCKS; z += RELIEF_STEP_BLOCKS) {
                    dx[index] = x;
                    dz[index] = z;
                    weirdnessWindow[index] = Math.abs(x) <= WEIRDNESS_RADIUS_BLOCKS && Math.abs(z) <= WEIRDNESS_RADIUS_BLOCKS;
                    if (z == 0 && x == SLOPE_STEP_BLOCKS) {
                        eastIndex = index;
                    } else if (z == 0 && x == -SLOPE_STEP_BLOCKS) {
                        westIndex = index;
                    } else if (x == 0 && z == SLOPE_STEP_BLOCKS) {
                        southIndex = index;
                    } else if (x == 0 && z == -SLOPE_STEP_BLOCKS) {
                        northIndex = index;
                    }
                    index++;
                }
            }

            if (eastIndex < 0 || westIndex < 0 || southIndex < 0 || northIndex < 0) {
                throw new IllegalStateException("Invalid terrain metric kernel configuration");
            }
            return new ReliefKernel(dx, dz, weirdnessWindow, sampleCount, eastIndex, westIndex, southIndex, northIndex);
        }
    }
}
