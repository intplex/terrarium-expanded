package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public record EarthGenerationProfile(
    int zoom,
    int maxMountainY,
    int oceanFloorY,
    String terrainBaseUrl,
    String biomesBaseUrl,
    String surfaceWaterBaseUrl,
    String terrainFixes,
    EarthWorldgenToggles worldgenToggles,
    boolean worldBorder,
    double spawnLatitude,
    double spawnLongitude
) {
    public static final String DEFAULT_TERRAIN_BASE_URL = "https://elevation-tiles-prod.s3.amazonaws.com/terrarium";
    public static final String DEFAULT_BIOMES_BASE_URL = "https://d127t6piqu53ls.cloudfront.net/tiles-reduced";
    public static final String DEFAULT_SURFACE_WATER_BASE_URL = "https://storage.googleapis.com/global-surface-water/tiles2021/seasonality";
    public static final double DEFAULT_SPAWN_LATITUDE = 0.442221;
    public static final double DEFAULT_SPAWN_LONGITUDE = 33.150150;
    public static final String TERRAIN_FIXES_NONE = "none";
    private static final EarthGenerationProfile DEFAULT = new EarthGenerationProfile(
        EarthGenConfig.DEFAULT_ZOOM,
        EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y,
        EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y,
        DEFAULT_TERRAIN_BASE_URL,
        DEFAULT_BIOMES_BASE_URL,
        DEFAULT_SURFACE_WATER_BASE_URL,
        TERRAIN_FIXES_NONE,
        EarthWorldgenToggles.defaults(),
        false,
        DEFAULT_SPAWN_LATITUDE,
        DEFAULT_SPAWN_LONGITUDE
    );

    public EarthGenerationProfile(int zoom, int maxMountainY, int oceanFloorY) {
        this(
            zoom,
            maxMountainY,
            oceanFloorY,
            DEFAULT_TERRAIN_BASE_URL,
            DEFAULT_BIOMES_BASE_URL,
            DEFAULT_SURFACE_WATER_BASE_URL,
            TERRAIN_FIXES_NONE,
            EarthWorldgenToggles.defaults(),
            false,
            DEFAULT_SPAWN_LATITUDE,
            DEFAULT_SPAWN_LONGITUDE
        );
    }

    public EarthGenerationProfile(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        String terrainBaseUrl,
        String biomesBaseUrl,
        String surfaceWaterBaseUrl,
        String terrainFixes,
        EarthWorldgenToggles worldgenToggles,
        boolean worldBorder
    ) {
        this(
            zoom,
            maxMountainY,
            oceanFloorY,
            terrainBaseUrl,
            biomesBaseUrl,
            surfaceWaterBaseUrl,
            terrainFixes,
            worldgenToggles,
            worldBorder,
            DEFAULT_SPAWN_LATITUDE,
            DEFAULT_SPAWN_LONGITUDE
        );
    }

    public EarthGenerationProfile {
        zoom = EarthGenConfig.validateZoom(zoom);
        maxMountainY = EarthGenConfig.validateMaxMountainY(maxMountainY);
        oceanFloorY = EarthGenConfig.validateOceanFloorY(oceanFloorY);
        terrainBaseUrl = normalizeUrl(terrainBaseUrl, "terrain_base_url");
        biomesBaseUrl = normalizeUrl(biomesBaseUrl, "biomes_base_url");
        surfaceWaterBaseUrl = normalizeUrl(surfaceWaterBaseUrl, "surface_water_base_url");
        terrainFixes = normalizeTerrainFixes(terrainFixes);
        worldgenToggles = worldgenToggles == null ? EarthWorldgenToggles.defaults() : worldgenToggles;
        spawnLatitude = validateSpawnLatitude(spawnLatitude);
        spawnLongitude = validateSpawnLongitude(spawnLongitude);
    }

    public EarthGenerationProfile withTerrainShape(int zoom, int maxMountainY, int oceanFloorY) {
        return new EarthGenerationProfile(
            zoom,
            maxMountainY,
            oceanFloorY,
            terrainBaseUrl,
            biomesBaseUrl,
            surfaceWaterBaseUrl,
            terrainFixes,
            worldgenToggles,
            worldBorder,
            spawnLatitude,
            spawnLongitude
        );
    }

    static EarthGenerationProfile defaults() {
        return DEFAULT;
    }

    private static String normalizeUrl(String raw, String label) {
        String value = Objects.requireNonNull(raw, label).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        URI.create(value);
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeTerrainFixes(String raw) {
        if (raw == null || raw.isBlank()) {
            return TERRAIN_FIXES_NONE;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static double validateSpawnLatitude(double latitude) {
        if (!Double.isFinite(latitude)) {
            throw new IllegalArgumentException("spawn_latitude must be finite");
        }
        if (latitude < -EarthGenConfig.MAX_MERCATOR_LATITUDE || latitude > EarthGenConfig.MAX_MERCATOR_LATITUDE) {
            throw new IllegalArgumentException(
                "Unsupported spawn_latitude "
                    + latitude
                    + "; supported range is "
                    + (-EarthGenConfig.MAX_MERCATOR_LATITUDE)
                    + "-"
                    + EarthGenConfig.MAX_MERCATOR_LATITUDE
            );
        }
        return latitude;
    }

    private static double validateSpawnLongitude(double longitude) {
        if (!Double.isFinite(longitude)) {
            throw new IllegalArgumentException("spawn_longitude must be finite");
        }
        if (longitude < EarthGenConfig.MIN_LONGITUDE || longitude > EarthGenConfig.MAX_LONGITUDE) {
            throw new IllegalArgumentException(
                "Unsupported spawn_longitude "
                    + longitude
                    + "; supported range is "
                    + EarthGenConfig.MIN_LONGITUDE
                    + "-"
                    + EarthGenConfig.MAX_LONGITUDE
            );
        }
        return longitude;
    }
}
