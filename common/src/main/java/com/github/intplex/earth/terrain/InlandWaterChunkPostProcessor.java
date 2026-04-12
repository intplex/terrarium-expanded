package com.github.intplex.earth.terrain;

import com.github.intplex.TerrariumExpanded;
import com.github.intplex.earth.EarthGenConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

public final class InlandWaterChunkPostProcessor {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    static final ResourceKey<NoiseGeneratorSettings> EARTH_NOISE_SETTINGS = ResourceKey.create(
        Registries.NOISE_SETTINGS,
        TerrariumExpanded.id("earth_overworld")
    );

    private InlandWaterChunkPostProcessor() {
    }

    public static boolean shouldProcess(NoiseBasedChunkGenerator generator) {
        return TerrainService.inlandWaterEnabled() && generator.stable(EARTH_NOISE_SETTINGS);
    }

    public static void fillChunk(ChunkAccess chunkAccess) {
        int minY = chunkAccess.getMinBuildHeight();
        int maxY = chunkAccess.getMaxBuildHeight();
        int chunkMinX = chunkAccess.getPos().getMinBlockX();
        int chunkMinZ = chunkAccess.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int blockX = chunkMinX + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int blockZ = chunkMinZ + localZ;
                if (TerrainService.inlandWaterKindAtXZ(blockX, blockZ) == WaterBodyKind.NONE) {
                    continue;
                }

                int waterSurfaceY = TerrainService.inlandWaterSurfaceYAtXZ(blockX, blockZ);
                int solidTopY = TerrainService.effectiveSolidTopYAtXZ(blockX, blockZ);
                int startY = Math.max(minY, solidTopY + 1);
                int endY = Math.min(maxY - 1, waterSurfaceY);
                for (int y = startY; y <= endY; y++) {
                    mutablePos.set(blockX, y, blockZ);
                    chunkAccess.setBlockState(mutablePos, WATER, false);
                }
            }
        }
    }
}
