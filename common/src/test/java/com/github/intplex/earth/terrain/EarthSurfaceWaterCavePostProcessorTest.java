package com.github.intplex.earth.terrain;

import com.mojang.serialization.Lifecycle;
import java.util.EnumSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthSurfaceWaterCavePostProcessorTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void floodsOnlyTheCaveConnectedToAValidOceanSurface() {
        ProtoChunk chunk = new ProtoChunk(
            new ChunkPos(0, 0),
            UpgradeData.EMPTY,
            LevelHeightAccessor.create(0, 80),
            testBiomeRegistry(),
            null
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 60; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }
            }
        }

        // The left half is a shallow ocean with a level surface at Y=62.
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlockState(pos.set(x, 61, z), Blocks.WATER.defaultBlockState(), false);
                chunk.setBlockState(pos.set(x, 62, z), Blocks.WATER.defaultBlockState(), false);
            }
        }

        // A coastal ravine breaches that ocean and branches beneath land.
        for (int y = 20; y <= 70; y++) {
            chunk.setBlockState(pos.set(7, y, 8), Blocks.CAVE_AIR.defaultBlockState(), false);
        }
        chunk.setBlockState(pos.set(7, 61, 8), Blocks.WATER.defaultBlockState(), false);
        chunk.setBlockState(pos.set(7, 62, 8), Blocks.WATER.defaultBlockState(), false);
        for (int x = 8; x <= 10; x++) {
            chunk.setBlockState(pos.set(x, 40, 8), Blocks.CAVE_AIR.defaultBlockState(), false);
        }

        // These caves have no opening to surface water and must stay dry,
        // including the one directly beneath an ocean column.
        chunk.setBlockState(pos.set(2, 40, 12), Blocks.CAVE_AIR.defaultBlockState(), false);
        chunk.setBlockState(pos.set(12, 40, 12), Blocks.CAVE_AIR.defaultBlockState(), false);
        Heightmap.primeHeightmaps(
            chunk,
            EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)
        );

        int filled = EarthSurfaceWaterCavePostProcessor.floodSurfaceConnectedCaves(
            chunk,
            Blocks.WATER.defaultBlockState(),
            (x, z) -> x < 8 ? 62 : EarthSurfaceWaterCavePostProcessor.NO_SURFACE_WATER,
            (x, z) -> 60
        );

        assertTrue(filled > 0);
        for (int y = 20; y <= 62; y++) {
            assertTrue(chunk.getBlockState(pos.set(7, y, 8)).is(Blocks.WATER));
        }
        assertTrue(chunk.getBlockState(pos.set(10, 40, 8)).is(Blocks.WATER));
        assertTrue(chunk.getBlockState(pos.set(2, 40, 12)).isAir());
        assertTrue(chunk.getBlockState(pos.set(12, 40, 12)).isAir());
        assertTrue(chunk.getBlockState(pos.set(7, 63, 8)).isAir());
        assertTrue(chunk.getBlockState(pos.set(8, 61, 8)).isAir());
        assertTrue(chunk.getBlockState(pos.set(7, 19, 8)).is(Blocks.STONE));
        assertEquals(62, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 7, 8));
        assertEquals(19, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 7, 8));
    }

    @Test
    void preservesEveryMappedLakeSurfaceInsteadOfSpreadingTheHighestWaterHead() {
        ProtoChunk chunk = new ProtoChunk(
            new ChunkPos(0, 0),
            UpgradeData.EMPTY,
            LevelHeightAccessor.create(0, 96),
            testBiomeRegistry(),
            null
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            int surfaceY = x < 8 ? 60 : 55;
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y <= 50; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }
                for (int y = 51; y <= surfaceY; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), false);
                }
            }
        }

        // The cave is connected to both halves beneath the mapped lake bed.
        for (int y = 30; y <= 50; y++) {
            chunk.setBlockState(pos.set(7, y, 8), Blocks.CAVE_AIR.defaultBlockState(), false);
        }
        for (int x = 8; x <= 10; x++) {
            chunk.setBlockState(pos.set(x, 40, 8), Blocks.CAVE_AIR.defaultBlockState(), false);
        }
        Heightmap.primeHeightmaps(
            chunk,
            EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)
        );

        EarthSurfaceWaterCavePostProcessor.floodSurfaceConnectedCaves(
            chunk,
            Blocks.WATER.defaultBlockState(),
            (x, z) -> x < 8 ? 60 : 55,
            (x, z) -> 50
        );

        assertTrue(chunk.getBlockState(pos.set(7, 30, 8)).is(Blocks.WATER));
        assertTrue(chunk.getBlockState(pos.set(10, 40, 8)).is(Blocks.WATER));
        assertTrue(chunk.getBlockState(pos.set(7, 60, 8)).is(Blocks.WATER));
        assertTrue(chunk.getBlockState(pos.set(8, 55, 8)).is(Blocks.WATER));
        for (int y = 56; y <= 60; y++) {
            assertTrue(chunk.getBlockState(pos.set(8, y, 8)).isAir());
        }
        assertEquals(60, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 7, 8));
        assertEquals(55, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8));
    }

    private static Registry<Biome> testBiomeRegistry() {
        Biome plains = new Biome.BiomeBuilder()
            .temperature(0.8F)
            .downfall(0.4F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .fogColor(0)
                    .waterColor(0)
                    .waterFogColor(0)
                    .skyColor(0)
                    .build()
            )
            .mobSpawnSettings(MobSpawnSettings.EMPTY)
            .generationSettings(BiomeGenerationSettings.EMPTY)
            .build();
        MappedRegistry<Biome> registry = new MappedRegistry<>(
            Registries.BIOME,
            Lifecycle.stable()
        );
        registry.register(Biomes.PLAINS, plains, RegistrationInfo.BUILT_IN);
        return registry.freeze();
    }
}
