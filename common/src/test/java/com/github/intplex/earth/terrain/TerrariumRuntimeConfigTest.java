package com.github.intplex.earth.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrariumRuntimeConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void missingConfigFallsBackToDefaults() {
        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.load(tempDir);

        assertEquals(TerrariumRuntimeConfig.DEFAULT_TERRAIN_CHUNK_CACHE_ENTRIES, config.terrainChunkCacheEntries());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TERRAIN_CHUNK_CACHE_TTL_SECONDS, config.terrainChunkCacheTtlSeconds());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_IO_THREADS_PER_SERVICE, config.ioThreadsPerService());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG, config.terrainTiles());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_RECOVERY_TILE_CONFIG, config.recoveryTiles());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_SURFACE_WATER_TILE_CONFIG, config.surfaceWaterTiles());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_ECOREGION_TILE_CONFIG, config.ecoregionTiles());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_SAMPLING_CONFIG, config.sampling());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_INLAND_WATER_CONFIG, config.inlandWater());
    }

    @Test
    void validConfigValuesAreParsed() throws IOException {
        writeConfig(
            "terrain.chunk_cache_entries=1024\n"
                + "terrain.chunk_cache_ttl_seconds=321\n"
                + "tiles.io_threads_per_service=3\n"
                + "tiles.terrain.cache_entries=111\n"
                + "tiles.terrain.prefetch_radius=1\n"
                + "tiles.terrain.cache_ttl_seconds=55\n"
                + "tiles.recovery.cache_entries=222\n"
                + "tiles.recovery.prefetch_radius=2\n"
                + "tiles.recovery.cache_ttl_seconds=66\n"
                + "tiles.surface_water.cache_entries=333\n"
                + "tiles.surface_water.prefetch_radius=3\n"
                + "tiles.surface_water.cache_ttl_seconds=77\n"
                + "tiles.ecoregion.cache_entries=44\n"
                + "tiles.ecoregion.prefetch_radius=4\n"
                + "tiles.ecoregion.cache_ttl_seconds=88\n"
                + "sampling.chunk_local_cache_entries=25\n"
                + "sampling.biome_local_cache_entries=6\n"
                + "sampling.thread_local_idle_seconds=9\n"
                + "inland_water.enabled=false\n"
                + "inland_water.min_water_months=6\n"
        );

        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.load(tempDir);
        assertEquals(1024, config.terrainChunkCacheEntries());
        assertEquals(321, config.terrainChunkCacheTtlSeconds());
        assertEquals(3, config.ioThreadsPerService());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(111, 1, 55), config.terrainTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(222, 2, 66), config.recoveryTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(333, 3, 77), config.surfaceWaterTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(44, 4, 88), config.ecoregionTiles());
        assertEquals(new TerrariumRuntimeConfig.SamplingConfig(25, 6, 9), config.sampling());
        assertFalse(config.inlandWater().enabled());
        assertEquals(6, config.inlandWater().minWaterMonths());
    }

    @Test
    void invalidAndOutOfRangeValuesUseFallbackAndClamping() throws IOException {
        writeConfig(
            "terrain.chunk_cache_entries=not-an-int\n"
                + "terrain.chunk_cache_ttl_seconds=-9\n"
                + "tiles.io_threads_per_service=0\n"
                + "tiles.terrain.cache_entries=-5\n"
                + "tiles.terrain.prefetch_radius=999\n"
                + "tiles.terrain.cache_ttl_seconds=invalid\n"
                + "tiles.recovery.cache_entries=8\n"
                + "tiles.recovery.prefetch_radius=-1\n"
                + "tiles.recovery.cache_ttl_seconds=999999\n"
                + "tiles.surface_water.cache_entries=\n"
                + "tiles.surface_water.prefetch_radius=garbage\n"
                + "tiles.surface_water.cache_ttl_seconds=-2\n"
                + "tiles.ecoregion.cache_entries=999999\n"
                + "tiles.ecoregion.prefetch_radius=9\n"
                + "tiles.ecoregion.cache_ttl_seconds=\n"
                + "sampling.chunk_local_cache_entries=0\n"
                + "sampling.biome_local_cache_entries=2000\n"
                + "sampling.thread_local_idle_seconds=999999\n"
                + "inland_water.enabled=maybe\n"
                + "inland_water.min_water_months=99\n"
        );

        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.load(tempDir);
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TERRAIN_CHUNK_CACHE_ENTRIES, config.terrainChunkCacheEntries());
        assertEquals(0, config.terrainChunkCacheTtlSeconds());
        assertEquals(1, config.ioThreadsPerService());
        assertEquals(1, config.terrainTiles().cacheEntries());
        assertEquals(8, config.terrainTiles().prefetchRadius());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TERRAIN_TILE_CONFIG.cacheTtlSeconds(), config.terrainTiles().cacheTtlSeconds());
        assertEquals(8, config.recoveryTiles().cacheEntries());
        assertEquals(0, config.recoveryTiles().prefetchRadius());
        assertEquals(86_400, config.recoveryTiles().cacheTtlSeconds());
        assertEquals(
            TerrariumRuntimeConfig.DEFAULT_SURFACE_WATER_TILE_CONFIG.cacheEntries(),
            config.surfaceWaterTiles().cacheEntries()
        );
        assertEquals(
            TerrariumRuntimeConfig.DEFAULT_SURFACE_WATER_TILE_CONFIG.prefetchRadius(),
            config.surfaceWaterTiles().prefetchRadius()
        );
        assertEquals(65_536, config.ecoregionTiles().cacheEntries());
        assertEquals(8, config.ecoregionTiles().prefetchRadius());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_ECOREGION_TILE_CONFIG.cacheTtlSeconds(), config.ecoregionTiles().cacheTtlSeconds());
        assertEquals(1, config.sampling().chunkLocalCacheEntries());
        assertEquals(1_024, config.sampling().biomeLocalCacheEntries());
        assertEquals(3_600, config.sampling().threadLocalIdleSeconds());
        assertTrue(config.inlandWater().enabled());
        assertEquals(12, config.inlandWater().minWaterMonths());
    }

    private void writeConfig(String body) throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(TerrariumRuntimeConfig.FILE_NAME), body);
    }
}
