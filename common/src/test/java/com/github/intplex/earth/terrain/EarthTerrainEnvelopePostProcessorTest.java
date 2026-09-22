package com.github.intplex.earth.terrain;

import com.mojang.serialization.Lifecycle;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthTerrainEnvelopePostProcessorTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void submergedSpaceAboveMappedTerrainIsAlwaysOceanFluid() {
        assertTrue(
            EarthTerrainEnvelopePostProcessor.stateAboveSurface(
                62,
                63,
                Blocks.WATER.defaultBlockState()
            ).is(Blocks.WATER)
        );
    }

    @Test
    void spaceAtAndAboveSeaLevelIsAlwaysAir() {
        assertTrue(
            EarthTerrainEnvelopePostProcessor.stateAboveSurface(
                63,
                63,
                Blocks.WATER.defaultBlockState()
            ).isAir()
        );
        assertTrue(
            EarthTerrainEnvelopePostProcessor.stateAboveSurface(
                120,
                63,
                Blocks.WATER.defaultBlockState()
            ).isAir()
        );
    }

    @Test
    void enforcementRemovesAquiferBarrierAboveFloorAndRestoresOceanColumn() {
        ProtoChunk chunk = new ProtoChunk(
            new ChunkPos(0, 0),
            UpgradeData.EMPTY,
            LevelHeightAccessor.create(-64, 384),
            PalettedContainerFactory.create(new RegistryAccess.ImmutableRegistryAccess(List.of(testBiomeRegistry()))),
            null
        );
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        chunk.setBlockState(pos.set(0, 58, 0), Blocks.STONE.defaultBlockState(), 0);
        chunk.setBlockState(pos.set(0, 60, 0), Blocks.STONE.defaultBlockState(), 0);
        chunk.setBlockState(pos.set(0, 64, 0), Blocks.STONE.defaultBlockState(), 0);
        Heightmap.primeHeightmaps(
            chunk,
            EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)
        );

        EarthTerrainEnvelopePostProcessor.enforceChunk(
            chunk,
            63,
            Blocks.WATER.defaultBlockState(),
            (x, z) -> 58
        );

        assertTrue(chunk.getBlockState(pos.set(0, 58, 0)).is(Blocks.STONE));
        for (int y = 59; y <= 62; y++) {
            assertTrue(chunk.getBlockState(pos.set(0, y, 0)).is(Blocks.WATER));
        }
        assertTrue(chunk.getBlockState(pos.set(0, 63, 0)).isAir());
        assertTrue(chunk.getBlockState(pos.set(0, 64, 0)).isAir());
        assertEquals(62, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0));
        assertEquals(58, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 0, 0));
        var oceanFluidPostProcessing = chunk.getPostProcessing()[chunk.getSectionIndex(59)];
        assertNotNull(oceanFluidPostProcessing);
        assertFalse(oceanFluidPostProcessing.isEmpty());
    }

    private static Registry<Biome> testBiomeRegistry() {
        Biome plains = new Biome.BiomeBuilder()
            .temperature(0.8F)
            .downfall(0.4F)
            .specialEffects(
                new BiomeSpecialEffects.Builder()
                    .waterColor(0)
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
