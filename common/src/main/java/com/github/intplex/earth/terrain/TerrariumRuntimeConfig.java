package com.github.intplex.earth.terrain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TerrariumRuntimeConfig {
    public static final String FILE_NAME = "terrarium-expanded.properties";

    public static final String KEY_TOTAL_BUDGET_MB = "memory.total_budget_mb";
    public static final String KEY_TILES_BUDGET_PERCENT = "memory.tiles_budget_percent";
    public static final String KEY_TILE_TTL_SECONDS = "memory.tile_ttl_seconds";
    public static final String KEY_SNAPSHOT_TTL_SECONDS = "memory.snapshot_ttl_seconds";
    public static final String KEY_LOCAL_CHUNK_ENTRIES = "memory.local_chunk_entries";
    public static final String KEY_LOCAL_BIOME_ENTRIES = "memory.local_biome_entries";
    public static final String KEY_LOCAL_IDLE_SECONDS = "memory.local_idle_seconds";
    public static final String KEY_SHARED_TILE_THREADS = "io.shared_tile_threads";
    public static final String KEY_TERRAIN_PREFETCH_RADIUS = "tiles.terrain.prefetch_radius";
    public static final String KEY_RECOVERY_PREFETCH_RADIUS = "tiles.recovery.prefetch_radius";
    public static final String KEY_SURFACE_WATER_PREFETCH_RADIUS = "tiles.surface_water.prefetch_radius";
    public static final String KEY_ECOREGION_PREFETCH_RADIUS = "tiles.ecoregion.prefetch_radius";
    public static final String KEY_INLAND_WATER_ENABLED = "inland_water.enabled";
    public static final String KEY_INLAND_WATER_MIN_WATER_MONTHS = "inland_water.min_water_months";

    public static final int DEFAULT_TOTAL_BUDGET_MB = 96;
    public static final int DEFAULT_TILES_BUDGET_PERCENT = 85;
    public static final int DEFAULT_TILE_TTL_SECONDS = 120;
    public static final int DEFAULT_SNAPSHOT_TTL_SECONDS = 120;
    public static final int DEFAULT_SHARED_TILE_THREADS = 4;
    public static final TileLayerConfig DEFAULT_TERRAIN_TILE_CONFIG = new TileLayerConfig(0);
    public static final TileLayerConfig DEFAULT_RECOVERY_TILE_CONFIG = new TileLayerConfig(0);
    public static final TileLayerConfig DEFAULT_SURFACE_WATER_TILE_CONFIG = new TileLayerConfig(0);
    public static final TileLayerConfig DEFAULT_ECOREGION_TILE_CONFIG = new TileLayerConfig(0);
    public static final SamplingConfig DEFAULT_SAMPLING_CONFIG = new SamplingConfig(16, 4, 10);
    public static final InlandWaterConfig DEFAULT_INLAND_WATER_CONFIG =
        new InlandWaterConfig(InlandWaterSettings.DEFAULT_ENABLED, InlandWaterSettings.DEFAULT_MIN_WATER_MONTHS);

    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final int MAX_TOTAL_BUDGET_MB = 8_192;
    private static final int MAX_TILES_BUDGET_PERCENT = 99;
    private static final int MAX_CACHE_TTL_SECONDS = 86_400;
    private static final int MAX_SHARED_TILE_THREADS = 64;
    private static final int MAX_PREFETCH_RADIUS = 8;
    private static final int MAX_LOCAL_CACHE_ENTRIES = 1_024;
    private static final int MAX_THREAD_LOCAL_IDLE_SECONDS = 3_600;
    private static final String[] UNSUPPORTED_LEGACY_KEYS = new String[] {
        "terrain.chunk_cache_entries",
        "terrain.chunk_cache_ttl_seconds",
        "tiles.io_threads_per_service",
        "tiles.terrain.cache_entries",
        "tiles.terrain.cache_ttl_seconds",
        "tiles.recovery.cache_entries",
        "tiles.recovery.cache_ttl_seconds",
        "tiles.surface_water.cache_entries",
        "tiles.surface_water.cache_ttl_seconds",
        "tiles.ecoregion.cache_entries",
        "tiles.ecoregion.cache_ttl_seconds",
        "sampling.chunk_local_cache_entries",
        "sampling.biome_local_cache_entries",
        "sampling.thread_local_idle_seconds"
    };

    private static final TerrariumRuntimeConfig DEFAULTS = new TerrariumRuntimeConfig(
        DEFAULT_TOTAL_BUDGET_MB,
        DEFAULT_TILES_BUDGET_PERCENT,
        DEFAULT_TILE_TTL_SECONDS,
        DEFAULT_SNAPSHOT_TTL_SECONDS,
        DEFAULT_SHARED_TILE_THREADS,
        DEFAULT_TERRAIN_TILE_CONFIG,
        DEFAULT_RECOVERY_TILE_CONFIG,
        DEFAULT_SURFACE_WATER_TILE_CONFIG,
        DEFAULT_ECOREGION_TILE_CONFIG,
        DEFAULT_SAMPLING_CONFIG,
        DEFAULT_INLAND_WATER_CONFIG
    );

    private final int totalBudgetMb;
    private final int tilesBudgetPercent;
    private final int tileTtlSeconds;
    private final int snapshotTtlSeconds;
    private final int sharedTileThreads;
    private final TileLayerConfig terrainTiles;
    private final TileLayerConfig recoveryTiles;
    private final TileLayerConfig surfaceWaterTiles;
    private final TileLayerConfig ecoregionTiles;
    private final SamplingConfig sampling;
    private final InlandWaterConfig inlandWater;

    private TerrariumRuntimeConfig(
        int totalBudgetMb,
        int tilesBudgetPercent,
        int tileTtlSeconds,
        int snapshotTtlSeconds,
        int sharedTileThreads,
        TileLayerConfig terrainTiles,
        TileLayerConfig recoveryTiles,
        TileLayerConfig surfaceWaterTiles,
        TileLayerConfig ecoregionTiles,
        SamplingConfig sampling,
        InlandWaterConfig inlandWater
    ) {
        this.totalBudgetMb = totalBudgetMb;
        this.tilesBudgetPercent = tilesBudgetPercent;
        this.tileTtlSeconds = tileTtlSeconds;
        this.snapshotTtlSeconds = snapshotTtlSeconds;
        this.sharedTileThreads = sharedTileThreads;
        this.terrainTiles = Objects.requireNonNull(terrainTiles, "terrainTiles");
        this.recoveryTiles = Objects.requireNonNull(recoveryTiles, "recoveryTiles");
        this.surfaceWaterTiles = Objects.requireNonNull(surfaceWaterTiles, "surfaceWaterTiles");
        this.ecoregionTiles = Objects.requireNonNull(ecoregionTiles, "ecoregionTiles");
        this.sampling = Objects.requireNonNull(sampling, "sampling");
        this.inlandWater = Objects.requireNonNull(inlandWater, "inlandWater");
    }

    public static TerrariumRuntimeConfig defaults() {
        return DEFAULTS;
    }

    public static TerrariumRuntimeConfig load(Path gameDir) {
        if (gameDir == null) {
            return defaults();
        }

        Path configPath = gameDir.resolve("config").resolve(FILE_NAME);
        if (!Files.exists(configPath)) {
            return defaults();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException exception) {
            LOGGER.warn("Failed reading runtime config {}: {}; using defaults", configPath, exception.toString());
            return defaults();
        }

        return fromProperties(properties, configPath);
    }

    int totalBudgetMb() {
        return totalBudgetMb;
    }

    int tilesBudgetPercent() {
        return tilesBudgetPercent;
    }

    int tileTtlSeconds() {
        return tileTtlSeconds;
    }

    int snapshotTtlSeconds() {
        return snapshotTtlSeconds;
    }

    int sharedTileThreads() {
        return sharedTileThreads;
    }

    TileLayerConfig terrainTiles() {
        return terrainTiles;
    }

    TileLayerConfig recoveryTiles() {
        return recoveryTiles;
    }

    TileLayerConfig surfaceWaterTiles() {
        return surfaceWaterTiles;
    }

    TileLayerConfig ecoregionTiles() {
        return ecoregionTiles;
    }

    SamplingConfig sampling() {
        return sampling;
    }

    InlandWaterConfig inlandWater() {
        return inlandWater;
    }

    long totalBudgetBytes() {
        return Math.max(1L, (long) totalBudgetMb * 1024L * 1024L);
    }

    long tileBudgetBytes() {
        long bytes = (totalBudgetBytes() * tilesBudgetPercent) / 100L;
        return Math.max(1L, bytes);
    }

    long snapshotBudgetBytes() {
        long bytes = totalBudgetBytes() - tileBudgetBytes();
        return Math.max(1L, bytes);
    }

    private static TerrariumRuntimeConfig fromProperties(Properties properties, Path configPath) {
        logUnsupportedLegacyKeys(properties, configPath);

        int totalBudgetMb = parseBoundedInt(properties, KEY_TOTAL_BUDGET_MB, DEFAULT_TOTAL_BUDGET_MB, 16, MAX_TOTAL_BUDGET_MB, configPath);
        int tilesBudgetPercent = parseBoundedInt(
            properties,
            KEY_TILES_BUDGET_PERCENT,
            DEFAULT_TILES_BUDGET_PERCENT,
            1,
            MAX_TILES_BUDGET_PERCENT,
            configPath
        );
        int tileTtlSeconds = parseBoundedInt(properties, KEY_TILE_TTL_SECONDS, DEFAULT_TILE_TTL_SECONDS, 0, MAX_CACHE_TTL_SECONDS, configPath);
        int snapshotTtlSeconds = parseBoundedInt(
            properties,
            KEY_SNAPSHOT_TTL_SECONDS,
            DEFAULT_SNAPSHOT_TTL_SECONDS,
            0,
            MAX_CACHE_TTL_SECONDS,
            configPath
        );
        int sharedTileThreads = parseBoundedInt(
            properties,
            KEY_SHARED_TILE_THREADS,
            DEFAULT_SHARED_TILE_THREADS,
            1,
            MAX_SHARED_TILE_THREADS,
            configPath
        );

        TileLayerConfig terrainTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_TERRAIN_PREFETCH_RADIUS, DEFAULT_TERRAIN_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath)
        );
        TileLayerConfig recoveryTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_RECOVERY_PREFETCH_RADIUS, DEFAULT_RECOVERY_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath)
        );
        TileLayerConfig surfaceWaterTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_SURFACE_WATER_PREFETCH_RADIUS, DEFAULT_SURFACE_WATER_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath)
        );
        TileLayerConfig ecoregionTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_ECOREGION_PREFETCH_RADIUS, DEFAULT_ECOREGION_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath)
        );
        SamplingConfig sampling = new SamplingConfig(
            parseBoundedInt(
                properties,
                KEY_LOCAL_CHUNK_ENTRIES,
                DEFAULT_SAMPLING_CONFIG.chunkLocalCacheEntries(),
                1,
                MAX_LOCAL_CACHE_ENTRIES,
                configPath
            ),
            parseBoundedInt(
                properties,
                KEY_LOCAL_BIOME_ENTRIES,
                DEFAULT_SAMPLING_CONFIG.biomeLocalCacheEntries(),
                1,
                MAX_LOCAL_CACHE_ENTRIES,
                configPath
            ),
            parseBoundedInt(
                properties,
                KEY_LOCAL_IDLE_SECONDS,
                DEFAULT_SAMPLING_CONFIG.threadLocalIdleSeconds(),
                0,
                MAX_THREAD_LOCAL_IDLE_SECONDS,
                configPath
            )
        );
        InlandWaterConfig inlandWater = new InlandWaterConfig(
            parseBoolean(properties, KEY_INLAND_WATER_ENABLED, DEFAULT_INLAND_WATER_CONFIG.enabled(), configPath),
            parseBoundedInt(
                properties,
                KEY_INLAND_WATER_MIN_WATER_MONTHS,
                DEFAULT_INLAND_WATER_CONFIG.minWaterMonths(),
                1,
                12,
                configPath
            )
        );

        return new TerrariumRuntimeConfig(
            totalBudgetMb,
            tilesBudgetPercent,
            tileTtlSeconds,
            snapshotTtlSeconds,
            sharedTileThreads,
            terrainTiles,
            recoveryTiles,
            surfaceWaterTiles,
            ecoregionTiles,
            sampling,
            inlandWater
        );
    }

    private static void logUnsupportedLegacyKeys(Properties properties, Path configPath) {
        for (String key : UNSUPPORTED_LEGACY_KEYS) {
            if (properties.containsKey(key)) {
                LOGGER.warn(
                    "Ignoring unsupported legacy key {} in {}; this key was removed in the memory-budget cache model",
                    key,
                    configPath
                );
            }
        }
    }

    private static int parseBoundedInt(
        Properties properties,
        String key,
        int defaultValue,
        int minInclusive,
        int maxInclusive,
        Path configPath
    ) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            LOGGER.warn(
                "Ignoring invalid integer {} for {} in {}; using default {}",
                raw,
                key,
                configPath,
                defaultValue
            );
            return defaultValue;
        }

        if (parsed < minInclusive) {
            LOGGER.warn(
                "Clamping {}={} to {} in {}",
                key,
                parsed,
                minInclusive,
                configPath
            );
            return minInclusive;
        }
        if (parsed > maxInclusive) {
            LOGGER.warn(
                "Clamping {}={} to {} in {}",
                key,
                parsed,
                maxInclusive,
                configPath
            );
            return maxInclusive;
        }
        return parsed;
    }

    private static boolean parseBoolean(Properties properties, String key, boolean defaultValue, Path configPath) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        LOGGER.warn(
            "Ignoring invalid boolean {} for {} in {}; using default {}",
            raw,
            key,
            configPath,
            defaultValue
        );
        return defaultValue;
    }

    public record TileLayerConfig(int prefetchRadius) {
        public TileLayerConfig {
            prefetchRadius = Math.max(0, prefetchRadius);
        }
    }

    public record SamplingConfig(int chunkLocalCacheEntries, int biomeLocalCacheEntries, int threadLocalIdleSeconds) {
        public SamplingConfig {
            chunkLocalCacheEntries = Math.max(1, chunkLocalCacheEntries);
            biomeLocalCacheEntries = Math.max(1, biomeLocalCacheEntries);
            threadLocalIdleSeconds = Math.max(0, threadLocalIdleSeconds);
        }
    }

    public record InlandWaterConfig(boolean enabled, int minWaterMonths) {
        public InlandWaterConfig {
            minWaterMonths = Math.max(1, Math.min(12, minWaterMonths));
        }
    }
}
