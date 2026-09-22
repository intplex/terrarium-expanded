package com.github.intplex.earth.terrain;

import java.util.Objects;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class EarthTerrainEnvelopePostProcessor {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private EarthTerrainEnvelopePostProcessor() {
    }

    /**
     * Restores the hard Earth surface invariant after vanilla density and
     * aquifer evaluation. Vanilla noise aquifers may return a solid barrier in
     * negative-density cells; those barriers are valid inside caves but must
     * never become terrain above the mapped Earth surface.
     */
    public static void enforceChunk(ChunkAccess chunkAccess, int seaLevel, BlockState oceanFluid) {
        enforceChunk(
            chunkAccess,
            seaLevel,
            oceanFluid,
            TerrainService::effectiveSolidTopYAtXZ
        );
    }

    static void enforceChunk(
        ChunkAccess chunkAccess,
        int seaLevel,
        BlockState oceanFluid,
        IntBinaryOperator solidTopAt
    ) {
        Objects.requireNonNull(chunkAccess, "chunkAccess");
        Objects.requireNonNull(oceanFluid, "oceanFluid");
        Objects.requireNonNull(solidTopAt, "solidTopAt");

        int minY = chunkAccess.getMinY();
        int maxY = chunkAccess.getMaxY() + 1;
        int chunkMinX = chunkAccess.getPos().getMinBlockX();
        int chunkMinZ = chunkAccess.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        Heightmap worldSurfaceHeightmap = chunkAccess.getOrCreateHeightmapUnprimed(
            Heightmap.Types.WORLD_SURFACE_WG
        );
        Heightmap oceanFloorHeightmap = chunkAccess.getOrCreateHeightmapUnprimed(
            Heightmap.Types.OCEAN_FLOOR_WG
        );

        for (int localX = 0; localX < 16; localX++) {
            int blockX = chunkMinX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockZ = chunkMinZ + localZ;
                int solidTopY = solidTopAt.applyAsInt(blockX, blockZ);
                int startY = Math.max(minY, solidTopY + 1);
                if (startY >= maxY) {
                    continue;
                }

                int generatedTopY = chunkAccess.getHeight(
                    Heightmap.Types.WORLD_SURFACE_WG,
                    blockX,
                    blockZ
                );
                int endY = Math.min(maxY - 1, Math.max(generatedTopY, seaLevel - 1));
                for (int y = startY; y <= endY; y++) {
                    BlockState expectedState = stateAboveSurface(y, seaLevel, oceanFluid);
                    mutablePos.set(blockX, y, blockZ);
                    if (!chunkAccess.getBlockState(mutablePos).equals(expectedState)) {
                        chunkAccess.setBlockState(mutablePos, expectedState, 0);
                        if (!expectedState.getFluidState().isEmpty()) {
                            chunkAccess.markPosForPostprocessing(mutablePos);
                        }
                        // The NOISE future completes before the ProtoChunk advances
                        // to the NOISE status, so setBlockState is not guaranteed to
                        // maintain the two worldgen heightmaps yet.
                        worldSurfaceHeightmap.update(localX, y, localZ, expectedState);
                        oceanFloorHeightmap.update(localX, y, localZ, expectedState);
                    }
                }
            }
        }
    }

    static BlockState stateAboveSurface(int blockY, int seaLevel, BlockState oceanFluid) {
        return blockY < seaLevel ? oceanFluid : AIR;
    }
}
