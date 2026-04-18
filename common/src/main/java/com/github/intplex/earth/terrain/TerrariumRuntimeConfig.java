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

    public static final String KEY_TERRAIN_CHUNK_CACHE_ENTRIES = "terrain.chunk_cache_entries";
    public static final String KEY_TERRAIN_CHUNK_CACHE_TTL_SECONDS = "terrain.chunk_cache_ttl_seconds";
    public static final String KEY_IO_THREADS_PER_SERVICE = "tiles.io_threads_per_service";
    public static final String KEY_TERRAIN_CACHE_ENTRIES = "tiles.terrain.cache_entries";
    public static final String KEY_TERRAIN_PREFETCH_RADIUS = "tiles.terrain.prefetch_radius";
    public static final String KEY_TERRAIN_CACHE_TTL_SECONDS = "tiles.terrain.cache_ttl_seconds";
    public static final String KEY_RECOVERY_CACHE_ENTRIES = "tiles.recovery.cache_entries";
    public static final String KEY_RECOVERY_PREFETCH_RADIUS = "tiles.recovery.prefetch_radius";
    public static final String KEY_RECOVERY_CACHE_TTL_SECONDS = "tiles.recovery.cache_ttl_seconds";
    public static final String KEY_SURFACE_WATER_CACHE_ENTRIES = "tiles.surface_water.cache_entries";
    public static final String KEY_SURFACE_WATER_PREFETCH_RADIUS = "tiles.surface_water.prefetch_radius";
    public static final String KEY_SURFACE_WATER_CACHE_TTL_SECONDS = "tiles.surface_water.cache_ttl_seconds";
    public static final String KEY_ECOREGION_CACHE_ENTRIES = "tiles.ecoregion.cache_entries";
    public static final String KEY_ECOREGION_PREFETCH_RADIUS = "tiles.ecoregion.prefetch_radius";
    public static final String KEY_ECOREGION_CACHE_TTL_SECONDS = "tiles.ecoregion.cache_ttl_seconds";
    public static final String KEY_SAMPLING_CHUNK_LOCAL_CACHE_ENTRIES = "sampling.chunk_local_cache_entries";
    public static final String KEY_SAMPLING_BIOME_LOCAL_CACHE_ENTRIES = "sampling.biome_local_cache_entries";
    public static final String KEY_SAMPLING_THREAD_LOCAL_IDLE_SECONDS = "sampling.thread_local_idle_seconds";
    public static final String KEY_INLAND_WATER_ENABLED = "inland_water.enabled";
    public static final String KEY_INLAND_WATER_MIN_WATER_MONTHS = "inland_water.min_water_months";

    public static final int DEFAULT_TERRAIN_CHUNK_CACHE_ENTRIES = 256;
    public static final int DEFAULT_TERRAIN_CHUNK_CACHE_TTL_SECONDS = 120;
    public static final int DEFAULT_IO_THREADS_PER_SERVICE = 2;
    public static final int DEFAULT_TILE_CACHE_TTL_SECONDS = 120;
    public static final TileLayerConfig DEFAULT_TERRAIN_TILE_CONFIG = new TileLayerConfig(64, 0, DEFAULT_TILE_CACHE_TTL_SECONDS);
    public static final TileLayerConfig DEFAULT_RECOVERY_TILE_CONFIG = new TileLayerConfig(64, 0, DEFAULT_TILE_CACHE_TTL_SECONDS);
    public static final TileLayerConfig DEFAULT_SURFACE_WATER_TILE_CONFIG = new TileLayerConfig(64, 0, DEFAULT_TILE_CACHE_TTL_SECONDS);
    public static final TileLayerConfig DEFAULT_ECOREGION_TILE_CONFIG = new TileLayerConfig(4, 0, DEFAULT_TILE_CACHE_TTL_SECONDS);
    public static final SamplingConfig DEFAULT_SAMPLING_CONFIG = new SamplingConfig(16, 4, 10);
    public static final InlandWaterConfig DEFAULT_INLAND_WATER_CONFIG =
        new InlandWaterConfig(InlandWaterSettings.DEFAULT_ENABLED, InlandWaterSettings.DEFAULT_MIN_WATER_MONTHS);

    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final int MAX_CACHE_ENTRIES = 65_536;
    private static final int MAX_PREFETCH_RADIUS = 8;
    private static final int MAX_IO_THREADS = 32;
    private static final int MAX_CACHE_TTL_SECONDS = 86_400;
    private static final int MAX_SAMPLING_CACHE_ENTRIES = 1_024;
    private static final int MAX_THREAD_LOCAL_IDLE_SECONDS = 3_600;

    private static final TerrariumRuntimeConfig DEFAULTS = new TerrariumRuntimeConfig(
        DEFAULT_TERRAIN_CHUNK_CACHE_ENTRIES,
        DEFAULT_TERRAIN_CHUNK_CACHE_TTL_SECONDS,
        DEFAULT_IO_THREADS_PER_SERVICE,
        DEFAULT_TERRAIN_TILE_CONFIG,
        DEFAULT_RECOVERY_TILE_CONFIG,
        DEFAULT_SURFACE_WATER_TILE_CONFIG,
        DEFAULT_ECOREGION_TILE_CONFIG,
        DEFAULT_SAMPLING_CONFIG,
        DEFAULT_INLAND_WATER_CONFIG
    );

    private final int terrainChunkCacheEntries;
    private final int terrainChunkCacheTtlSeconds;
    private final int ioThreadsPerService;
    private final TileLayerConfig terrainTiles;
    private final TileLayerConfig recoveryTiles;
    private final TileLayerConfig surfaceWaterTiles;
    private final TileLayerConfig ecoregionTiles;
    private final SamplingConfig sampling;
    private final InlandWaterConfig inlandWater;

    private TerrariumRuntimeConfig(
        int terrainChunkCacheEntries,
        int terrainChunkCacheTtlSeconds,
        int ioThreadsPerService,
        TileLayerConfig terrainTiles,
        TileLayerConfig recoveryTiles,
        TileLayerConfig surfaceWaterTiles,
        TileLayerConfig ecoregionTiles,
        SamplingConfig sampling,
        InlandWaterConfig inlandWater
    ) {
        this.terrainChunkCacheEntries = terrainChunkCacheEntries;
        this.terrainChunkCacheTtlSeconds = terrainChunkCacheTtlSeconds;
        this.ioThreadsPerService = ioThreadsPerService;
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

    int terrainChunkCacheEntries() {
        return terrainChunkCacheEntries;
    }

    int terrainChunkCacheTtlSeconds() {
        return terrainChunkCacheTtlSeconds;
    }

    int ioThreadsPerService() {
        return ioThreadsPerService;
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

    private static TerrariumRuntimeConfig fromProperties(Properties properties, Path configPath) {
        int chunkCacheEntries = parseBoundedInt(
            properties,
            KEY_TERRAIN_CHUNK_CACHE_ENTRIES,
            DEFAULT_TERRAIN_CHUNK_CACHE_ENTRIES,
            1,
            MAX_CACHE_ENTRIES,
            configPath
        );
        int chunkCacheTtlSeconds = parseBoundedInt(
            properties,
            KEY_TERRAIN_CHUNK_CACHE_TTL_SECONDS,
            DEFAULT_TERRAIN_CHUNK_CACHE_TTL_SECONDS,
            0,
            MAX_CACHE_TTL_SECONDS,
            configPath
        );
        int ioThreadsPerService = parseBoundedInt(
            properties,
            KEY_IO_THREADS_PER_SERVICE,
            DEFAULT_IO_THREADS_PER_SERVICE,
            1,
            MAX_IO_THREADS,
            configPath
        );

        TileLayerConfig terrainTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_TERRAIN_CACHE_ENTRIES, DEFAULT_TERRAIN_TILE_CONFIG.cacheEntries(), 1, MAX_CACHE_ENTRIES, configPath),
            parseBoundedInt(properties, KEY_TERRAIN_PREFETCH_RADIUS, DEFAULT_TERRAIN_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath),
            parseBoundedInt(properties, KEY_TERRAIN_CACHE_TTL_SECONDS, DEFAULT_TERRAIN_TILE_CONFIG.cacheTtlSeconds(), 0, MAX_CACHE_TTL_SECONDS, configPath)
        );
        TileLayerConfig recoveryTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_RECOVERY_CACHE_ENTRIES, DEFAULT_RECOVERY_TILE_CONFIG.cacheEntries(), 1, MAX_CACHE_ENTRIES, configPath),
            parseBoundedInt(properties, KEY_RECOVERY_PREFETCH_RADIUS, DEFAULT_RECOVERY_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath),
            parseBoundedInt(properties, KEY_RECOVERY_CACHE_TTL_SECONDS, DEFAULT_RECOVERY_TILE_CONFIG.cacheTtlSeconds(), 0, MAX_CACHE_TTL_SECONDS, configPath)
        );
        TileLayerConfig surfaceWaterTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_SURFACE_WATER_CACHE_ENTRIES, DEFAULT_SURFACE_WATER_TILE_CONFIG.cacheEntries(), 1, MAX_CACHE_ENTRIES, configPath),
            parseBoundedInt(properties, KEY_SURFACE_WATER_PREFETCH_RADIUS, DEFAULT_SURFACE_WATER_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath),
            parseBoundedInt(
                properties,
                KEY_SURFACE_WATER_CACHE_TTL_SECONDS,
                DEFAULT_SURFACE_WATER_TILE_CONFIG.cacheTtlSeconds(),
                0,
                MAX_CACHE_TTL_SECONDS,
                configPath
            )
        );
        TileLayerConfig ecoregionTiles = new TileLayerConfig(
            parseBoundedInt(properties, KEY_ECOREGION_CACHE_ENTRIES, DEFAULT_ECOREGION_TILE_CONFIG.cacheEntries(), 1, MAX_CACHE_ENTRIES, configPath),
            parseBoundedInt(properties, KEY_ECOREGION_PREFETCH_RADIUS, DEFAULT_ECOREGION_TILE_CONFIG.prefetchRadius(), 0, MAX_PREFETCH_RADIUS, configPath),
            parseBoundedInt(properties, KEY_ECOREGION_CACHE_TTL_SECONDS, DEFAULT_ECOREGION_TILE_CONFIG.cacheTtlSeconds(), 0, MAX_CACHE_TTL_SECONDS, configPath)
        );
        SamplingConfig sampling = new SamplingConfig(
            parseBoundedInt(
                properties,
                KEY_SAMPLING_CHUNK_LOCAL_CACHE_ENTRIES,
                DEFAULT_SAMPLING_CONFIG.chunkLocalCacheEntries(),
                1,
                MAX_SAMPLING_CACHE_ENTRIES,
                configPath
            ),
            parseBoundedInt(
                properties,
                KEY_SAMPLING_BIOME_LOCAL_CACHE_ENTRIES,
                DEFAULT_SAMPLING_CONFIG.biomeLocalCacheEntries(),
                1,
                MAX_SAMPLING_CACHE_ENTRIES,
                configPath
            ),
            parseBoundedInt(
                properties,
                KEY_SAMPLING_THREAD_LOCAL_IDLE_SECONDS,
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
            chunkCacheEntries,
            chunkCacheTtlSeconds,
            ioThreadsPerService,
            terrainTiles,
            recoveryTiles,
            surfaceWaterTiles,
            ecoregionTiles,
            sampling,
            inlandWater
        );
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

    public record TileLayerConfig(int cacheEntries, int prefetchRadius, int cacheTtlSeconds) {
        public TileLayerConfig {
            cacheEntries = Math.max(1, cacheEntries);
            prefetchRadius = Math.max(0, prefetchRadius);
            cacheTtlSeconds = Math.max(0, cacheTtlSeconds);
        }

        public TileLayerConfig(int cacheEntries, int prefetchRadius) {
            this(cacheEntries, prefetchRadius, DEFAULT_TILE_CACHE_TTL_SECONDS);
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
