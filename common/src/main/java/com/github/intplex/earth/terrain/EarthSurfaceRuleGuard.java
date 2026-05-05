package com.github.intplex.earth.terrain;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class EarthSurfaceRuleGuard {
    private static final ThreadLocal<GuardContext> ACTIVE_CONTEXT = new ThreadLocal<>();
    private static final int UNCOMPUTED = Integer.MIN_VALUE;
    private static final int NO_INTERIOR_AIR = Integer.MIN_VALUE + 1;

    private EarthSurfaceRuleGuard() {
    }

    public static void runForChunk(ChunkAccess chunkAccess, Runnable action) {
        GuardContext previous = ACTIVE_CONTEXT.get();
        ACTIVE_CONTEXT.set(new GuardContext(chunkAccess));
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTIVE_CONTEXT.remove();
            } else {
                ACTIVE_CONTEXT.set(previous);
            }
        }
    }

    public static boolean shouldSkipSurfaceRule(ChunkAccess chunkAccess, int blockX, int blockY, int blockZ) {
        GuardContext context = ACTIVE_CONTEXT.get();
        if (context == null || context.chunkAccess != chunkAccess) {
            return false;
        }
        int solidTopY = TerrainService.effectiveSolidTopYAtXZ(blockX, blockZ);
        if (solidTopY <= TerrainService.OUT_OF_BOUNDS_SOLID_TOP_Y || blockY >= solidTopY) {
            return false;
        }
        int highestInteriorAirY = context.highestInteriorAirY(blockX, blockZ, solidTopY);
        return highestInteriorAirY != NO_INTERIOR_AIR && blockY < highestInteriorAirY;
    }

    static boolean shouldSkipSurfaceRuleForTesting(int blockY, int solidTopY, int highestInteriorAirY) {
        return solidTopY > TerrainService.OUT_OF_BOUNDS_SOLID_TOP_Y
            && blockY < solidTopY
            && highestInteriorAirY != NO_INTERIOR_AIR
            && blockY < highestInteriorAirY;
    }

    static int noInteriorAirForTesting() {
        return NO_INTERIOR_AIR;
    }

    private static final class GuardContext {
        private final ChunkAccess chunkAccess;
        private final int[] highestInteriorAirByColumn = new int[16 * 16];
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        private GuardContext(ChunkAccess chunkAccess) {
            this.chunkAccess = chunkAccess;
            Arrays.fill(highestInteriorAirByColumn, UNCOMPUTED);
        }

        private int highestInteriorAirY(int blockX, int blockZ, int solidTopY) {
            int localX = blockX - chunkAccess.getPos().getMinBlockX();
            int localZ = blockZ - chunkAccess.getPos().getMinBlockZ();
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                return NO_INTERIOR_AIR;
            }

            int index = localZ * 16 + localX;
            int cached = highestInteriorAirByColumn[index];
            if (cached != UNCOMPUTED) {
                return cached;
            }

            int minY = chunkAccess.getMinY();
            int maxY = chunkAccess.getMinY() + chunkAccess.getHeight() - 1;
            int startY = Math.min(maxY, solidTopY - 1);
            for (int y = startY; y >= minY; y--) {
                mutablePos.set(blockX, y, blockZ);
                if (chunkAccess.getBlockState(mutablePos).isAir()) {
                    highestInteriorAirByColumn[index] = y;
                    return y;
                }
            }

            highestInteriorAirByColumn[index] = NO_INTERIOR_AIR;
            return NO_INTERIOR_AIR;
        }
    }
}
