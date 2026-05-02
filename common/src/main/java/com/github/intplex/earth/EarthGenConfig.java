package com.github.intplex.earth;

import com.github.intplex.earth.terrain.TileKey;
import java.util.Optional;

public final class EarthGenConfig {
    public static final int MIN_ZOOM = 8;
    public static final int MAX_ZOOM = 12;
    public static final int DEFAULT_ZOOM = 8;
    public static final int ECOREGION_SOURCE_ZOOM = 8;
    @Deprecated(forRemoval = false)
    public static final int ZOOM = DEFAULT_ZOOM;
    /**
     * WWF Ecoregions raster tiles are currently authored only up to z=8.
     * Treat this as the highest available quality level for biome sourcing.
     */
    @Deprecated(forRemoval = false)
    public static final int ECOREGION_ZOOM = ECOREGION_SOURCE_ZOOM;
    public static final int TILE_SIZE = 256;
    @Deprecated(forRemoval = false)
    public static final int EARTH_BLOCK_SPAN = blockSpanForZoom(DEFAULT_ZOOM);
    public static final int ECOREGION_REDUCED_GROUP_SIZE = 4;
    public static final int ECOREGION_REDUCED_TILE_SIZE = TILE_SIZE * ECOREGION_REDUCED_GROUP_SIZE;
    public static final int ECOREGION_REDUCED_TILE_COUNT_PER_AXIS = blockSpanForZoom(ECOREGION_SOURCE_ZOOM) / ECOREGION_REDUCED_TILE_SIZE;
    @Deprecated(forRemoval = false)
    public static final int WATER_ZOOM = DEFAULT_ZOOM;
    @Deprecated(forRemoval = false)
    public static final int HALF_SPAN = EARTH_BLOCK_SPAN / 2;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;
    public static final double MAX_MERCATOR_LATITUDE = 85.0511287798066;

    public static final int SEA_LEVEL = 63;
    /**
     * Vanilla noise settings enforce an absolute upper build limit of y=2031
     * (i.e. min_y + height - 1 <= 2031). This is an engine cap, not a per-world cap.
     */
    public static final int ABSOLUTE_MAX_TERRAIN_Y = 2031;
    public static final int MIN_Y = -64;
    public static final int MIN_TERRAIN_Y = 0;
    public static final int DEFAULT_MAX_MOUNTAIN_Y = 256;
    public static final int DEFAULT_OCEAN_FLOOR_Y = MIN_TERRAIN_Y;
    public static final int MIN_MAX_MOUNTAIN_Y = SEA_LEVEL;
    public static final int MAX_OCEAN_FLOOR_Y = SEA_LEVEL - 1;

    public static final double MAX_ABOVE_SEA_METERS = 8900.0;
    public static final double OCEAN_FLOOR_DEPTH_METERS = 4000.0;
    private static final double EARTH_EQUATOR_CIRCUMFERENCE_METERS = 40075016.68557849;
    private static volatile int activeZoom = DEFAULT_ZOOM;
    private static volatile int activeMaxTerrainY = DEFAULT_MAX_MOUNTAIN_Y;
    private static volatile int configuredMaxMountainY = DEFAULT_MAX_MOUNTAIN_Y;
    private static volatile int activeMaxMountainY = DEFAULT_MAX_MOUNTAIN_Y;
    private static volatile int activeOceanFloorY = DEFAULT_OCEAN_FLOOR_Y;

    private EarthGenConfig() {
    }

    public static boolean isSupportedZoom(int zoom) {
        return zoom >= MIN_ZOOM && zoom <= MAX_ZOOM;
    }

    public static int validateZoom(int zoom) {
        if (!isSupportedZoom(zoom)) {
            throw new IllegalArgumentException("Unsupported Earth zoom " + zoom + "; supported zooms are " + MIN_ZOOM + "-" + MAX_ZOOM);
        }
        return zoom;
    }

    public static int activeZoom() {
        return activeZoom;
    }

    public static void setActiveZoom(int zoom) {
        activeZoom = validateZoom(zoom);
    }

    public static int activeWaterZoom() {
        return waterSourceZoomForWorldZoom(activeZoom());
    }

    public static int activeTerrainZoom() {
        return activeZoom();
    }

    public static int activeMaxMountainY() {
        return activeMaxMountainY;
    }

    public static int activeMaxTerrainY() {
        return activeMaxTerrainY;
    }

    public static int activeOceanFloorY() {
        return activeOceanFloorY;
    }

    public static int validateMaxMountainY(int y) {
        return validateMaxMountainY(y, activeMaxTerrainY);
    }

    public static int validateMaxMountainY(int y, int maxTerrainY) {
        if (maxTerrainY < MIN_MAX_MOUNTAIN_Y || maxTerrainY > ABSOLUTE_MAX_TERRAIN_Y) {
            throw new IllegalArgumentException(
                "Unsupported max terrain Y " + maxTerrainY + "; supported range is " + MIN_MAX_MOUNTAIN_Y + "-" + ABSOLUTE_MAX_TERRAIN_Y
            );
        }
        if (y < MIN_MAX_MOUNTAIN_Y || y > maxTerrainY) {
            throw new IllegalArgumentException(
                "Unsupported max_mountain_y " + y + "; supported range is " + MIN_MAX_MOUNTAIN_Y + "-" + maxTerrainY
            );
        }
        return y;
    }

    public static int validateOceanFloorY(int y) {
        if (y < MIN_TERRAIN_Y || y > MAX_OCEAN_FLOOR_Y) {
            throw new IllegalArgumentException(
                "Unsupported ocean_floor_y " + y + "; supported range is " + MIN_TERRAIN_Y + "-" + MAX_OCEAN_FLOOR_Y
            );
        }
        return y;
    }

    public static boolean setActiveTerrainProfile(int maxMountainY, int oceanFloorY) {
        int validatedMaxMountainY = validateMaxMountainY(maxMountainY, ABSOLUTE_MAX_TERRAIN_Y);
        int resolvedMaxMountainY = Math.min(validatedMaxMountainY, activeMaxTerrainY);
        int validatedOceanFloorY = validateOceanFloorY(oceanFloorY);
        boolean changed = configuredMaxMountainY != validatedMaxMountainY
            || activeMaxMountainY != resolvedMaxMountainY
            || activeOceanFloorY != validatedOceanFloorY;
        configuredMaxMountainY = validatedMaxMountainY;
        activeMaxMountainY = resolvedMaxMountainY;
        activeOceanFloorY = validatedOceanFloorY;
        return changed;
    }

    public static boolean setActiveMaxTerrainY(int maxTerrainY) {
        int validatedMaxTerrainY = validateMaxTerrainY(maxTerrainY);
        boolean changed = activeMaxTerrainY != validatedMaxTerrainY;
        activeMaxTerrainY = validatedMaxTerrainY;
        int resolvedMaxMountainY = Math.min(configuredMaxMountainY, validatedMaxTerrainY);
        if (activeMaxMountainY != resolvedMaxMountainY) {
            activeMaxMountainY = resolvedMaxMountainY;
            changed = true;
        }
        return changed;
    }

    public static int maxTerrainYFromBuildHeight(int maxBuildHeightExclusive) {
        return validateMaxTerrainY(maxBuildHeightExclusive - 1);
    }

    public static int maxTerrainYFromVerticalRange(int minY, int height) {
        return validateMaxTerrainY(minY + height - 1);
    }

    public static int maxTerrainYFromNoiseSettings(int minY, int height) {
        return maxTerrainYFromVerticalRange(minY, height);
    }

    public static void resetActiveTerrainProfile() {
        configuredMaxMountainY = DEFAULT_MAX_MOUNTAIN_Y;
        activeMaxMountainY = Math.min(configuredMaxMountainY, activeMaxTerrainY);
        activeOceanFloorY = DEFAULT_OCEAN_FLOOR_Y;
    }

    public static int blockSpan() {
        return blockSpanForZoom(activeZoom());
    }

    public static int halfSpan() {
        return halfSpanForZoom(activeZoom());
    }

    public static int terrainTileCountPerAxis() {
        return tileCountPerAxis(activeZoom());
    }

    public static int blockSpanForZoom(int zoom) {
        return TILE_SIZE << validateZoom(zoom);
    }

    public static int halfSpanForZoom(int zoom) {
        return blockSpanForZoom(zoom) / 2;
    }

    public static int tileCountPerAxis(int zoom) {
        return 1 << validateZoom(zoom);
    }

    public static double metersPerBlockForZoom(int zoom) {
        return EARTH_EQUATOR_CIRCUMFERENCE_METERS / blockSpanForZoom(zoom);
    }

    public static Optional<TileSamplePoint> projectBlockToTile(int blockX, int blockZ) {
        return projectBlockToTile(blockX, blockZ, activeZoom());
    }

    public static Optional<TileSamplePoint> projectBlockToTile(int blockX, int blockZ, int zoom) {
        return Optional.ofNullable(projectBlockToTileInternal(blockX, blockZ, validateZoom(zoom), validateZoom(zoom), TILE_SIZE));
    }

    public static Optional<TileSamplePoint> projectBlockToTerrainTile(int blockX, int blockZ) {
        return projectBlockToTerrainTile(blockX, blockZ, activeZoom());
    }

    public static Optional<TileSamplePoint> projectBlockToTerrainTile(int blockX, int blockZ, int zoom) {
        return projectBlockToTile(blockX, blockZ, zoom);
    }

    public static Optional<TileSamplePoint> projectBlockToEcoregionTile(int blockX, int blockZ) {
        return projectBlockToEcoregionTile(blockX, blockZ, activeZoom());
    }

    public static Optional<TileSamplePoint> projectBlockToEcoregionTile(int blockX, int blockZ, int zoom) {
        return Optional.ofNullable(
            projectBlockToTileInternal(blockX, blockZ, validateZoom(zoom), ECOREGION_SOURCE_ZOOM, ECOREGION_REDUCED_TILE_SIZE)
        );
    }

    public static int waterSourceZoomForWorldZoom(int zoom) {
        return validateZoom(zoom);
    }

    public static Optional<GeoCoordinates> blockToGeo(int blockX, int blockZ) {
        return blockToGeo(blockX, blockZ, activeZoom());
    }

    public static Optional<GeoCoordinates> blockToGeo(int blockX, int blockZ, int zoom) {
        int validatedZoom = validateZoom(zoom);
        GlobalPixel globalPixel = toGlobalPixelOrNull(blockX, blockZ, validatedZoom);
        if (globalPixel == null) {
            return Optional.empty();
        }

        double span = blockSpanForZoom(validatedZoom);
        double normalizedX = globalPixel.x() / span;
        double normalizedY = globalPixel.y() / span;
        double longitude = normalizedX * 360.0 - 180.0;
        double mercatorN = Math.PI * (1.0 - (2.0 * normalizedY));
        double latitude = Math.toDegrees(Math.atan(Math.sinh(mercatorN)));
        return Optional.of(new GeoCoordinates(latitude, longitude));
    }

    public static Optional<BlockCoordinates> geoToBlock(double latitude, double longitude) {
        return geoToBlock(latitude, longitude, activeZoom());
    }

    public static Optional<BlockCoordinates> geoToBlock(double latitude, double longitude, int zoom) {
        int validatedZoom = validateZoom(zoom);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return Optional.empty();
        }
        if (latitude < -MAX_MERCATOR_LATITUDE || latitude > MAX_MERCATOR_LATITUDE) {
            return Optional.empty();
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            return Optional.empty();
        }

        double normalizedX = (longitude - MIN_LONGITUDE) / 360.0;
        double latitudeRadians = Math.toRadians(latitude);
        double normalizedY = (1.0 - (Math.log(Math.tan(latitudeRadians) + (1.0 / Math.cos(latitudeRadians))) / Math.PI)) / 2.0;

        int span = blockSpanForZoom(validatedZoom);
        int globalX = clampGlobalPixel((int) Math.floor(normalizedX * span), validatedZoom);
        int globalY = clampGlobalPixel((int) Math.floor(normalizedY * span), validatedZoom);
        int halfSpan = halfSpanForZoom(validatedZoom);
        return Optional.of(new BlockCoordinates(globalX - halfSpan, globalY - halfSpan));
    }

    public static double decodeTerrariumMeters(int red, int green, int blue) {
        return (red * 256.0 + green + blue / 256.0) - 32768.0;
    }

    public static int mapMetersToTerrainY(double meters) {
        int landLevelOffset = 1;
        int seaLevelOffset = 1;
        int maxMountainY = activeMaxMountainY;
        int oceanFloorY = activeOceanFloorY;
        int baseY = SEA_LEVEL - seaLevelOffset;

        if (meters >= 0.0) {
            // clamped in [0, 1.0]
            double clamped = Math.min(meters, MAX_ABOVE_SEA_METERS) / MAX_ABOVE_SEA_METERS;
            // 0 => SEA_LEVEL - landLevelOffset, 1.0 => active max mountain Y
            return (int) ((SEA_LEVEL - landLevelOffset) + clamped * (maxMountainY - (SEA_LEVEL - landLevelOffset)));
        }

        // clamped in [0, 1.0]
        double clamped = Math.min(-meters, OCEAN_FLOOR_DEPTH_METERS) / OCEAN_FLOOR_DEPTH_METERS;
        // 0 => SEA_LEVEL - seaLevelOffset, 1.0 => active ocean floor Y
        return (int) (baseY + clamped * (oceanFloorY - baseY));
    }

    private static int validateMaxTerrainY(int y) {
        if (y < MIN_MAX_MOUNTAIN_Y || y > ABSOLUTE_MAX_TERRAIN_Y) {
            throw new IllegalArgumentException(
                "Unsupported max terrain Y " + y + "; supported range is " + MIN_MAX_MOUNTAIN_Y + "-" + ABSOLUTE_MAX_TERRAIN_Y
            );
        }
        return y;
    }

    private static GlobalPixel toGlobalPixelOrNull(int blockX, int blockZ, int zoom) {
        int halfSpan = halfSpanForZoom(zoom);
        int span = blockSpanForZoom(zoom);
        int globalPxX = blockX + halfSpan;
        int globalPxY = blockZ + halfSpan;
        if (globalPxX < 0 || globalPxX >= span || globalPxY < 0 || globalPxY >= span) {
            return null;
        }
        return new GlobalPixel(globalPxX, globalPxY);
    }

    private static int clampGlobalPixel(int value, int zoom) {
        return Math.max(0, Math.min(blockSpanForZoom(zoom) - 1, value));
    }

    private static TileSamplePoint projectBlockToTileInternal(
        int blockX,
        int blockZ,
        int worldZoom,
        int sourceZoom,
        int tileSize
    ) {
        if (sourceZoom > worldZoom) {
            throw new IllegalArgumentException("Source zoom " + sourceZoom + " cannot exceed world zoom " + worldZoom);
        }
        int halfSpan = halfSpanForZoom(worldZoom);
        int span = blockSpanForZoom(worldZoom);
        int globalPxX = blockX + halfSpan;
        int globalPxY = blockZ + halfSpan;
        if (globalPxX < 0 || globalPxX >= span || globalPxY < 0 || globalPxY >= span) {
            return null;
        }
        int scaleShift = worldZoom - sourceZoom;
        int sourceGlobalX = globalPxX >> scaleShift;
        int sourceGlobalY = globalPxY >> scaleShift;
        return projectGlobalPixelToTile(sourceGlobalX, sourceGlobalY, tileSize);
    }

    private static TileSamplePoint projectGlobalPixelToTile(int globalPxX, int globalPxY, int tileSize) {
        int tileX = Math.floorDiv(globalPxX, tileSize);
        int tileY = Math.floorDiv(globalPxY, tileSize);
        int localX = Math.floorMod(globalPxX, tileSize);
        int localY = Math.floorMod(globalPxY, tileSize);
        return new TileSamplePoint(new TileKey(tileX, tileY), localX, localY);
    }

    public record TileSamplePoint(TileKey tileKey, int pixelX, int pixelY) {
    }

    private record GlobalPixel(int x, int y) {
    }

    public record GeoCoordinates(double latitude, double longitude) {
    }

    public record BlockCoordinates(int x, int z) {
    }
}
