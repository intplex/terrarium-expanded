package com.github.intplex.earth.terrain;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;

public final class EarthSurfaceWaterCavePostProcessor {
    static final int NO_SURFACE_WATER = Integer.MIN_VALUE;
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_AREA = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final int[] NEIGHBOR_X = {0, 0, -1, 1, 0, 0};
    private static final int[] NEIGHBOR_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] NEIGHBOR_Z = {0, 0, 0, 0, -1, 1};

    private EarthSurfaceWaterCavePostProcessor() {
    }

    /**
     * Floods air carved into a surface ocean, lake, or river. Propagation is
     * connectivity-based and capped by each column's mapped water or terrain
     * surface, so a sealed cave remains dry, a coastal opening fills only to
     * sea level, and one high inland-water sample cannot flatten a lake.
     */
    public static int floodSurfaceConnectedCaves(
        ChunkAccess chunkAccess,
        int seaLevel,
        BlockState surfaceFluid,
        boolean includeInlandWater
    ) {
        return floodSurfaceConnectedCaves(
            chunkAccess,
            surfaceFluid,
            (blockX, blockZ) -> surfaceWaterYAt(
                blockX,
                blockZ,
                seaLevel,
                includeInlandWater
            ),
            TerrainService::effectiveSolidTopYAtXZ
        );
    }

    static int floodSurfaceConnectedCaves(
        ChunkAccess chunkAccess,
        BlockState surfaceFluid,
        IntBinaryOperator surfaceWaterYAt,
        IntBinaryOperator solidTopYAt
    ) {
        Objects.requireNonNull(chunkAccess, "chunkAccess");
        Objects.requireNonNull(surfaceFluid, "surfaceFluid");
        Objects.requireNonNull(surfaceWaterYAt, "surfaceWaterYAt");
        Objects.requireNonNull(solidTopYAt, "solidTopYAt");

        if (surfaceFluid.getFluidState().isEmpty()) {
            return 0;
        }

        int minY = chunkAccess.getMinY();
        int maxY = chunkAccess.getMaxY() + 1;
        int chunkMinX = chunkAccess.getPos().getMinBlockX();
        int chunkMinZ = chunkAccess.getPos().getMinBlockZ();
        int[] columnWaterSurfaces = new int[CHUNK_AREA];
        int[] columnFillCeilings = new int[CHUNK_AREA];
        Arrays.fill(columnWaterSurfaces, NO_SURFACE_WATER);
        int highestWaterSurface = NO_SURFACE_WATER;

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int blockZ = chunkMinZ + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int blockX = chunkMinX + localX;
                int columnIndex = columnIndex(localX, localZ);
                int surfaceY = surfaceWaterYAt.applyAsInt(blockX, blockZ);
                if (surfaceY < minY || surfaceY >= maxY) {
                    // A connected cave may extend beneath a dry neighboring
                    // column, but the repair must never turn that column's
                    // exposed air into a lake extension.
                    columnFillCeilings[columnIndex] = solidTopYAt.applyAsInt(blockX, blockZ);
                    continue;
                }
                columnWaterSurfaces[columnIndex] = surfaceY;
                // Inland-water heights can legitimately differ between
                // adjacent columns. Treat the mapped height as a hard local
                // ceiling rather than spreading the highest water head across
                // the entire connected surface.
                columnFillCeilings[columnIndex] = surfaceY;
                highestWaterSurface = Math.max(highestWaterSurface, surfaceY);
            }
        }

        if (highestWaterSurface == NO_SURFACE_WATER) {
            return 0;
        }

        int floodHeight = highestWaterSurface - minY + 1;
        int[] waterHeads = new int[floodHeight * CHUNK_AREA];
        Arrays.fill(waterHeads, NO_SURFACE_WATER);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        Fluid surfaceFluidType = surfaceFluid.getFluidState().getType();

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int surfaceY = columnWaterSurfaces[columnIndex(localX, localZ)];
                if (surfaceY == NO_SURFACE_WATER) {
                    continue;
                }
                int index = blockIndex(localX, surfaceY - minY, localZ);
                mutablePos.set(chunkMinX + localX, surfaceY, chunkMinZ + localZ);
                BlockState seedState = chunkAccess.getBlockState(mutablePos);
                if (hasFluid(seedState, surfaceFluidType)) {
                    waterHeads[index] = surfaceY;
                    queue.enqueue(index);
                }
            }
        }

        Heightmap worldSurfaceHeightmap = chunkAccess.getOrCreateHeightmapUnprimed(
            Heightmap.Types.WORLD_SURFACE_WG
        );
        Heightmap oceanFloorHeightmap = chunkAccess.getOrCreateHeightmapUnprimed(
            Heightmap.Types.OCEAN_FLOOR_WG
        );
        int filledBlocks = 0;

        while (queue.size() > 0) {
            int index = queue.dequeueInt();
            int waterHead = waterHeads[index];
            int relativeY = index / CHUNK_AREA;
            int columnIndex = index % CHUNK_AREA;
            int localZ = columnIndex / CHUNK_WIDTH;
            int localX = columnIndex % CHUNK_WIDTH;
            int blockY = minY + relativeY;
            mutablePos.set(chunkMinX + localX, blockY, chunkMinZ + localZ);
            BlockState currentState = chunkAccess.getBlockState(mutablePos);

            if (currentState.isAir() || isNonSourceSurfaceFluid(currentState, surfaceFluid)) {
                chunkAccess.setBlockState(mutablePos, surfaceFluid, 0);
                worldSurfaceHeightmap.update(localX, blockY, localZ, surfaceFluid);
                oceanFloorHeightmap.update(localX, blockY, localZ, surfaceFluid);
                if (localX == 0 || localX == 15 || localZ == 0 || localZ == 15) {
                    chunkAccess.markPosForPostprocessing(mutablePos);
                }
                filledBlocks++;
            }

            for (int direction = 0; direction < NEIGHBOR_X.length; direction++) {
                int neighborX = localX + NEIGHBOR_X[direction];
                int neighborRelativeY = relativeY + NEIGHBOR_Y[direction];
                int neighborZ = localZ + NEIGHBOR_Z[direction];
                if (
                    neighborX < 0
                        || neighborX >= CHUNK_WIDTH
                        || neighborRelativeY < 0
                        || neighborRelativeY >= floodHeight
                        || neighborZ < 0
                        || neighborZ >= CHUNK_WIDTH
                ) {
                    continue;
                }
                int neighborColumnIndex = columnIndex(neighborX, neighborZ);
                int neighborWaterHead = Math.min(
                    waterHead,
                    columnFillCeilings[neighborColumnIndex]
                );
                if (minY + neighborRelativeY > neighborWaterHead) {
                    continue;
                }
                tryReach(
                    chunkAccess,
                    surfaceFluidType,
                    chunkMinX,
                    chunkMinZ,
                    minY,
                    blockIndex(neighborX, neighborRelativeY, neighborZ),
                    neighborWaterHead,
                    waterHeads,
                    queue,
                    mutablePos
                );
            }
        }

        return filledBlocks;
    }

    private static int surfaceWaterYAt(
        int blockX,
        int blockZ,
        int seaLevel,
        boolean includeInlandWater
    ) {
        int surfaceY = NO_SURFACE_WATER;
        int solidTopY = TerrainService.effectiveSolidTopYAtXZ(blockX, blockZ);
        if (solidTopY < seaLevel - 1) {
            surfaceY = seaLevel - 1;
        }
        if (
            includeInlandWater
                && TerrainService.inlandWaterKindAtXZ(blockX, blockZ) != WaterBodyKind.NONE
        ) {
            surfaceY = Math.max(
                surfaceY,
                TerrainService.inlandWaterSurfaceYAtXZ(blockX, blockZ)
            );
        }
        return surfaceY;
    }

    private static void tryReach(
        ChunkAccess chunkAccess,
        Fluid surfaceFluidType,
        int chunkMinX,
        int chunkMinZ,
        int minY,
        int index,
        int waterHead,
        int[] waterHeads,
        IntArrayFIFOQueue queue,
        BlockPos.MutableBlockPos mutablePos
    ) {
        if (waterHead <= waterHeads[index]) {
            return;
        }
        int relativeY = index / CHUNK_AREA;
        int columnIndex = index % CHUNK_AREA;
        int localZ = columnIndex / CHUNK_WIDTH;
        int localX = columnIndex % CHUNK_WIDTH;
        int blockY = minY + relativeY;
        if (blockY > waterHead) {
            return;
        }

        mutablePos.set(chunkMinX + localX, blockY, chunkMinZ + localZ);
        BlockState state = chunkAccess.getBlockState(mutablePos);
        if (!state.isAir() && !hasFluid(state, surfaceFluidType)) {
            return;
        }

        waterHeads[index] = waterHead;
        queue.enqueue(index);
    }

    private static boolean hasFluid(BlockState state, Fluid surfaceFluidType) {
        return !state.getFluidState().isEmpty()
            && state.getFluidState().getType() == surfaceFluidType;
    }

    private static boolean isNonSourceSurfaceFluid(BlockState state, BlockState surfaceFluid) {
        return state.getBlock() == surfaceFluid.getBlock() && !state.equals(surfaceFluid);
    }

    private static int columnIndex(int localX, int localZ) {
        return localZ * CHUNK_WIDTH + localX;
    }

    private static int blockIndex(int localX, int relativeY, int localZ) {
        return relativeY * CHUNK_AREA + columnIndex(localX, localZ);
    }
}
