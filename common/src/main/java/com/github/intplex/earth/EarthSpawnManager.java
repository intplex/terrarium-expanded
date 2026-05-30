package com.github.intplex.earth;

import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EarthSpawnManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final int WORLD_BORDER_INSET_CHUNKS = 32;
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final double WORLD_BORDER_CENTER_X = 0.0;
    private static final double WORLD_BORDER_CENTER_Z = 0.0;
    private static final double BORDER_EPSILON = 0.001;

    private EarthSpawnManager() {
    }

    public static void forceSpawnAtOrigin(MinecraftServer server) {
        forceSpawnAtOrigin(server, "unspecified");
    }

    public static void forceSpawnAtOrigin(MinecraftServer server, String phase) {
        forceSpawnFromPreset(server, phase);
    }

    public static void forceSpawnFromPreset(MinecraftServer server, String phase) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }

        BlockPos previousSpawn = overworld.getSharedSpawnPos();
        SpawnPoint spawnPoint = resolveSpawnPoint(overworld);
        BlockPos spawnPos = spawnPoint.blockPos();
        overworld.setDefaultSpawnPos(spawnPos, 0.0F);
        applyWorldBorderFromPreset(overworld, phase);
        LOGGER.info(
            "[TX-SPAWN] forcing overworld spawn during {} previous=({}, {}, {}) new=({}, {}, {}) source={} lat={} lon={}",
            phase,
            previousSpawn.getX(),
            previousSpawn.getY(),
            previousSpawn.getZ(),
            spawnPos.getX(),
            spawnPos.getY(),
            spawnPos.getZ(),
            spawnPoint.source(),
            spawnPoint.latitude(),
            spawnPoint.longitude()
        );
    }

    private static void applyWorldBorderFromPreset(ServerLevel overworld, String phase) {
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return;
        }
        BiomeSource biomeSource = noiseGenerator.getBiomeSource();
        if (!(biomeSource instanceof EcoregionBiomeSource earthBiomeSource)) {
            return;
        }
        if (!earthBiomeSource.worldBorder()) {
            return;
        }

        int insetBlocks = WORLD_BORDER_INSET_CHUNKS * BLOCKS_PER_CHUNK;
        int worldSpanBlocks = EarthGenConfig.blockSpanForZoom(earthBiomeSource.zoom());
        int borderSizeBlocks = Math.max(1, worldSpanBlocks - insetBlocks * 2);
        double targetSize = borderSizeBlocks;

        var border = overworld.getWorldBorder();
        if (Math.abs(border.getCenterX() - WORLD_BORDER_CENTER_X) <= BORDER_EPSILON
            && Math.abs(border.getCenterZ() - WORLD_BORDER_CENTER_Z) <= BORDER_EPSILON
            && Math.abs(border.getSize() - targetSize) <= BORDER_EPSILON) {
            return;
        }

        border.setCenter(WORLD_BORDER_CENTER_X, WORLD_BORDER_CENTER_Z);
        border.setSize(targetSize);
        LOGGER.info(
            "[TX-BORDER] applied world border during {} inset_chunks={} zoom={} size={} center=({}, {})",
            phase,
            WORLD_BORDER_INSET_CHUNKS,
            earthBiomeSource.zoom(),
            borderSizeBlocks,
            WORLD_BORDER_CENTER_X,
            WORLD_BORDER_CENTER_Z
        );
    }

    private static SpawnPoint resolveSpawnPoint(ServerLevel overworld) {
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return new SpawnPoint(new BlockPos(0, EarthGenConfig.DEFAULT_SEA_LEVEL, 0), 0.0, 0.0, "legacy_origin");
        }
        BiomeSource biomeSource = noiseGenerator.getBiomeSource();
        if (!(biomeSource instanceof EcoregionBiomeSource earthBiomeSource)) {
            return new SpawnPoint(new BlockPos(0, EarthGenConfig.DEFAULT_SEA_LEVEL, 0), 0.0, 0.0, "legacy_origin");
        }

        EarthGenConfig.BlockCoordinates configuredBlock = EarthGenConfig
            .geoToBlock(earthBiomeSource.spawnLatitude(), earthBiomeSource.spawnLongitude(), earthBiomeSource.zoom())
            .orElseGet(() -> EarthGenConfig.geoToBlock(
                EarthGenerationProfile.DEFAULT_SPAWN_LATITUDE,
                EarthGenerationProfile.DEFAULT_SPAWN_LONGITUDE,
                earthBiomeSource.zoom()
            ).orElse(new EarthGenConfig.BlockCoordinates(0, 0)));
        BlockPos spawnPos = new BlockPos(configuredBlock.x(), earthBiomeSource.seaLevel(), configuredBlock.z());
        return new SpawnPoint(
            spawnPos,
            earthBiomeSource.spawnLatitude(),
            earthBiomeSource.spawnLongitude(),
            "earth_preset"
        );
    }

    private record SpawnPoint(BlockPos blockPos, double latitude, double longitude, String source) {
    }
}
