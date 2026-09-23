package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public record EarthGenerationProfile(
    int zoom,
    int maxMountainY,
    int oceanFloorY,
    int seaLevel,
    TerrainHeightMode belowSeaHeightMode,
    TerrainHeightMode aboveSeaHeightMode,
    String terrainBaseUrl,
    String biomesBaseUrl,
    String surfaceWaterBaseUrl,
    String terrainFixes,
    EarthWorldgenToggles worldgenToggles,
    boolean worldBorder,
    double spawnLatitude,
    double spawnLongitude,
    InlandWaterSettings inlandWater
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
        EarthGenConfig.DEFAULT_SEA_LEVEL,
        TerrainHeightMode.EVEN_SCALE,
        TerrainHeightMode.EVEN_SCALE,
        DEFAULT_TERRAIN_BASE_URL,
        DEFAULT_BIOMES_BASE_URL,
        DEFAULT_SURFACE_WATER_BASE_URL,
        TERRAIN_FIXES_NONE,
        EarthWorldgenToggles.defaults(),
        false,
        DEFAULT_SPAWN_LATITUDE,
        DEFAULT_SPAWN_LONGITUDE
    );

    // A flat map codec preserves the existing biome-source JSON/NBT layout.
    public static final MapCodec<EarthGenerationProfile> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.intRange(EarthGenConfig.MIN_ZOOM, EarthGenConfig.MAX_ZOOM)
            .optionalFieldOf("zoom", EarthGenConfig.DEFAULT_ZOOM).forGetter(EarthGenerationProfile::zoom),
        Codec.intRange(EarthGenConfig.MIN_MAX_MOUNTAIN_Y, EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y)
            .optionalFieldOf("max_mountain_y", EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y).forGetter(EarthGenerationProfile::maxMountainY),
        Codec.intRange(EarthGenConfig.MIN_TERRAIN_Y, EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y - 2)
            .optionalFieldOf("ocean_floor_y", EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y).forGetter(EarthGenerationProfile::oceanFloorY),
        Codec.intRange(EarthGenConfig.MIN_SEA_LEVEL, EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y - 1)
            .optionalFieldOf("sea_level", EarthGenConfig.DEFAULT_SEA_LEVEL).forGetter(EarthGenerationProfile::seaLevel),
        TerrainHeightModes.CODEC.forGetter(profile -> new TerrainHeightModes(profile.belowSeaHeightMode(), profile.aboveSeaHeightMode())),
        Codec.STRING.optionalFieldOf("terrain_base_url", DEFAULT_TERRAIN_BASE_URL).forGetter(EarthGenerationProfile::terrainBaseUrl),
        Codec.STRING.optionalFieldOf("biomes_base_url", DEFAULT_BIOMES_BASE_URL).forGetter(EarthGenerationProfile::biomesBaseUrl),
        Codec.STRING.optionalFieldOf("surface_water_base_url", DEFAULT_SURFACE_WATER_BASE_URL).forGetter(EarthGenerationProfile::surfaceWaterBaseUrl),
        Codec.STRING.optionalFieldOf("terrain_fixes", TERRAIN_FIXES_NONE).forGetter(EarthGenerationProfile::terrainFixes),
        Codec.BOOL.optionalFieldOf("world_border", false).forGetter(EarthGenerationProfile::worldBorder),
        Codec.doubleRange(-EarthGenConfig.MAX_MERCATOR_LATITUDE, EarthGenConfig.MAX_MERCATOR_LATITUDE)
            .optionalFieldOf("spawn_latitude", DEFAULT_SPAWN_LATITUDE).forGetter(EarthGenerationProfile::spawnLatitude),
        Codec.doubleRange(EarthGenConfig.MIN_LONGITUDE, EarthGenConfig.MAX_LONGITUDE)
            .optionalFieldOf("spawn_longitude", DEFAULT_SPAWN_LONGITUDE).forGetter(EarthGenerationProfile::spawnLongitude),
        EarthWorldgenToggles.CODEC.codec().optionalFieldOf("generation", EarthWorldgenToggles.defaults())
            .forGetter(EarthGenerationProfile::worldgenToggles),
        InlandWaterSettings.WORLDGEN_CODEC.forGetter(EarthGenerationProfile::inlandWater)
    ).apply(instance, (zoom, mountain, floor, sea, modes, terrainUrl, biomesUrl, waterUrl, fixes, border, latitude, longitude, generation, water) ->
        new EarthGenerationProfile(zoom, mountain, floor, sea, modes.belowSea(), modes.aboveSea(), terrainUrl, biomesUrl,
            waterUrl, fixes, generation, border, latitude, longitude, water)));

    public EarthGenerationProfile(
        int zoom, int maxMountainY, int oceanFloorY, int seaLevel,
        TerrainHeightMode belowSeaHeightMode, TerrainHeightMode aboveSeaHeightMode,
        String terrainBaseUrl, String biomesBaseUrl, String surfaceWaterBaseUrl,
        String terrainFixes, EarthWorldgenToggles worldgenToggles, boolean worldBorder,
        double spawnLatitude, double spawnLongitude
    ) {
        this(zoom, maxMountainY, oceanFloorY, seaLevel, belowSeaHeightMode, aboveSeaHeightMode,
            terrainBaseUrl, biomesBaseUrl, surfaceWaterBaseUrl, terrainFixes, worldgenToggles,
            worldBorder, spawnLatitude, spawnLongitude, InlandWaterSettings.DEFAULT);
    }

    public EarthGenerationProfile(int zoom, int maxMountainY, int oceanFloorY) {
        this(zoom, maxMountainY, oceanFloorY, EarthGenConfig.DEFAULT_SEA_LEVEL);
    }

    public EarthGenerationProfile(int zoom, int maxMountainY, int oceanFloorY, int seaLevel) {
        this(
            zoom,
            maxMountainY,
            oceanFloorY,
            seaLevel,
            TerrainHeightMode.EVEN_SCALE,
            TerrainHeightMode.EVEN_SCALE,
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
            EarthGenConfig.DEFAULT_SEA_LEVEL,
            TerrainHeightMode.EVEN_SCALE,
            TerrainHeightMode.EVEN_SCALE,
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

    public EarthGenerationProfile(
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
        this(
            zoom,
            maxMountainY,
            oceanFloorY,
            EarthGenConfig.DEFAULT_SEA_LEVEL,
            TerrainHeightMode.EVEN_SCALE,
            TerrainHeightMode.EVEN_SCALE,
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

    public EarthGenerationProfile(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        int seaLevel,
        String terrainBaseUrl,
        String biomesBaseUrl,
        String surfaceWaterBaseUrl,
        String terrainFixes,
        EarthWorldgenToggles worldgenToggles,
        boolean worldBorder,
        double spawnLatitude,
        double spawnLongitude
    ) {
        this(
            zoom,
            maxMountainY,
            oceanFloorY,
            seaLevel,
            TerrainHeightMode.EVEN_SCALE,
            TerrainHeightMode.EVEN_SCALE,
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

    public EarthGenerationProfile {
        zoom = EarthGenConfig.validateZoom(zoom);
        seaLevel = EarthGenConfig.validateSeaLevel(seaLevel, maxMountainY, oceanFloorY);
        maxMountainY = EarthGenConfig.validateMaxMountainY(maxMountainY, EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y, seaLevel);
        oceanFloorY = EarthGenConfig.validateOceanFloorY(oceanFloorY, seaLevel);
        belowSeaHeightMode = TerrainHeightMode.normalize(belowSeaHeightMode);
        aboveSeaHeightMode = TerrainHeightMode.normalize(aboveSeaHeightMode);
        terrainBaseUrl = normalizeUrl(terrainBaseUrl, "terrain_base_url");
        biomesBaseUrl = normalizeUrl(biomesBaseUrl, "biomes_base_url");
        surfaceWaterBaseUrl = normalizeUrl(surfaceWaterBaseUrl, "surface_water_base_url");
        terrainFixes = normalizeTerrainFixes(terrainFixes);
        worldgenToggles = worldgenToggles == null ? EarthWorldgenToggles.defaults() : worldgenToggles;
        inlandWater = Objects.requireNonNull(inlandWater, "inlandWater");
        spawnLatitude = validateSpawnLatitude(spawnLatitude);
        spawnLongitude = validateSpawnLongitude(spawnLongitude);
    }

    public EarthGenerationProfile withTerrainShape(int zoom, int maxMountainY, int oceanFloorY) {
        return withTerrainShape(zoom, maxMountainY, oceanFloorY, seaLevel, belowSeaHeightMode, aboveSeaHeightMode);
    }

    public EarthGenerationProfile withTerrainShape(int zoom, int maxMountainY, int oceanFloorY, int seaLevel) {
        return withTerrainShape(zoom, maxMountainY, oceanFloorY, seaLevel, belowSeaHeightMode, aboveSeaHeightMode);
    }

    public EarthGenerationProfile withTerrainShape(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        int seaLevel,
        TerrainHeightMode belowSeaHeightMode,
        TerrainHeightMode aboveSeaHeightMode
    ) {
        return new EarthGenerationProfile(
            zoom,
            maxMountainY,
            oceanFloorY,
            seaLevel,
            belowSeaHeightMode,
            aboveSeaHeightMode,
            terrainBaseUrl,
            biomesBaseUrl,
            surfaceWaterBaseUrl,
            terrainFixes,
            worldgenToggles,
            worldBorder,
            spawnLatitude,
            spawnLongitude,
            inlandWater
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
