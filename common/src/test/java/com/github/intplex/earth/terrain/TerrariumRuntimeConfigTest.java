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

        assertEquals(TerrariumRuntimeConfig.DEFAULT_TOTAL_BUDGET_MB, config.totalBudgetMb());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TILES_BUDGET_PERCENT, config.tilesBudgetPercent());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TILE_TTL_SECONDS, config.tileTtlSeconds());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_SNAPSHOT_TTL_SECONDS, config.snapshotTtlSeconds());
        assertEquals(TerrariumRuntimeConfig.DEFAULT_SHARED_TILE_THREADS, config.sharedTileThreads());
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
            "memory.total_budget_mb=384\n"
                + "memory.tiles_budget_percent=70\n"
                + "memory.tile_ttl_seconds=55\n"
                + "memory.snapshot_ttl_seconds=66\n"
                + "memory.local_chunk_entries=25\n"
                + "memory.local_biome_entries=6\n"
                + "memory.local_idle_seconds=9\n"
                + "io.shared_tile_threads=7\n"
                + "tiles.terrain.prefetch_radius=1\n"
                + "tiles.recovery.prefetch_radius=2\n"
                + "tiles.surface_water.prefetch_radius=3\n"
                + "tiles.ecoregion.prefetch_radius=4\n"
                + "inland_water.enabled=false\n"
                + "inland_water.min_water_months=6\n"
        );

        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.load(tempDir);
        assertEquals(384, config.totalBudgetMb());
        assertEquals(70, config.tilesBudgetPercent());
        assertEquals(55, config.tileTtlSeconds());
        assertEquals(66, config.snapshotTtlSeconds());
        assertEquals(7, config.sharedTileThreads());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(1), config.terrainTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(2), config.recoveryTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(3), config.surfaceWaterTiles());
        assertEquals(new TerrariumRuntimeConfig.TileLayerConfig(4), config.ecoregionTiles());
        assertEquals(new TerrariumRuntimeConfig.SamplingConfig(25, 6, 9), config.sampling());
        assertFalse(config.inlandWater().enabled());
        assertEquals(6, config.inlandWater().minWaterMonths());
        assertTrue(config.tileBudgetBytes() > 0L);
        assertTrue(config.snapshotBudgetBytes() > 0L);
    }

    @Test
    void invalidOutOfRangeAndLegacyValuesUseClampingAndDefaults() throws IOException {
        writeConfig(
            "memory.total_budget_mb=invalid\n"
                + "memory.tiles_budget_percent=999\n"
                + "memory.tile_ttl_seconds=-1\n"
                + "memory.snapshot_ttl_seconds=999999\n"
                + "memory.local_chunk_entries=0\n"
                + "memory.local_biome_entries=2000\n"
                + "memory.local_idle_seconds=999999\n"
                + "io.shared_tile_threads=0\n"
                + "tiles.terrain.prefetch_radius=999\n"
                + "tiles.recovery.prefetch_radius=-1\n"
                + "tiles.surface_water.prefetch_radius=garbage\n"
                + "tiles.ecoregion.prefetch_radius=9\n"
                + "terrain.chunk_cache_entries=9000\n"
                + "terrain.chunk_cache_ttl_seconds=9000\n"
                + "tiles.io_threads_per_service=9\n"
                + "tiles.terrain.cache_entries=999\n"
                + "tiles.terrain.cache_ttl_seconds=999\n"
                + "sampling.chunk_local_cache_entries=999\n"
                + "sampling.biome_local_cache_entries=999\n"
                + "sampling.thread_local_idle_seconds=999\n"
                + "inland_water.enabled=maybe\n"
                + "inland_water.min_water_months=99\n"
        );

        TerrariumRuntimeConfig config = TerrariumRuntimeConfig.load(tempDir);
        assertEquals(TerrariumRuntimeConfig.DEFAULT_TOTAL_BUDGET_MB, config.totalBudgetMb());
        assertEquals(99, config.tilesBudgetPercent());
        assertEquals(0, config.tileTtlSeconds());
        assertEquals(86_400, config.snapshotTtlSeconds());
        assertEquals(1, config.sharedTileThreads());
        assertEquals(8, config.terrainTiles().prefetchRadius());
        assertEquals(0, config.recoveryTiles().prefetchRadius());
        assertEquals(
            TerrariumRuntimeConfig.DEFAULT_SURFACE_WATER_TILE_CONFIG.prefetchRadius(),
            config.surfaceWaterTiles().prefetchRadius()
        );
        assertEquals(8, config.ecoregionTiles().prefetchRadius());
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
