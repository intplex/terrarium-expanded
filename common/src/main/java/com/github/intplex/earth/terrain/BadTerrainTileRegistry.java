package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BadTerrainTileRegistry {
    static final String RESOURCE_PATH = "/data/terrarium_expanded/terrain/bad_tile_recovery.geojson";
    static final String EXTERNAL_CONFIG_FILE_NAME = "bad_tile_recovery.geojson";
    static final int DEFAULT_MIN_TARGET_ZOOM = 9;
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static volatile Map<TargetTile, Integer> cachedSourceZoomByTargetTile;
    private static volatile Path initializedGameDir;

    private BadTerrainTileRegistry() {
    }

    public static void initialize(Path gameDir) {
        Path normalizedGameDir = normalizeGameDir(gameDir);
        synchronized (BadTerrainTileRegistry.class) {
            if (Objects.equals(initializedGameDir, normalizedGameDir)) {
                return;
            }
            initializedGameDir = normalizedGameDir;
            cachedSourceZoomByTargetTile = null;
        }
    }

    public static void validateStartupRegistry() {
        requireSourceZoomByTargetTile();
    }

    public static OptionalInt sourceZoomFor(int targetZoom, TileKey targetTileKey) {
        int validatedZoom = EarthGenConfig.validateZoom(targetZoom);
        Integer sourceZoom = requireSourceZoomByTargetTile().get(new TargetTile(validatedZoom, targetTileKey));
        return sourceZoom == null ? OptionalInt.empty() : OptionalInt.of(sourceZoom);
    }

    public static Set<Integer> sourceZoomsForTargetZoom(int targetZoom) {
        int validatedZoom = EarthGenConfig.validateZoom(targetZoom);
        Set<Integer> sourceZooms = new TreeSet<>();
        for (Map.Entry<TargetTile, Integer> entry : requireSourceZoomByTargetTile().entrySet()) {
            if (entry.getKey().targetZoom() == validatedZoom) {
                sourceZooms.add(entry.getValue());
            }
        }
        return Set.copyOf(sourceZooms);
    }

    static Map<TargetTile, Integer> parseMappingsGeoJson(Reader reader) {
        return parseMappingsGeoJson(reader, "geojson");
    }

    static Map<TargetTile, Integer> loadMappingsForTesting(String bundledGeoJson, String externalGeoJson) {
        Map<TargetTile, Integer> bundled = parseMappingsGeoJson(
            new StringReader(Objects.requireNonNull(bundledGeoJson, "bundledGeoJson")),
            "bundled test geojson"
        );
        if (externalGeoJson == null || externalGeoJson.isBlank()) {
            return bundled;
        }
        try {
            Map<TargetTile, Integer> external = parseMappingsGeoJson(
                new StringReader(externalGeoJson),
                "external test geojson"
            );
            return mergeMappings(bundled, external);
        } catch (IllegalStateException exception) {
            return bundled;
        }
    }

    static void resetForTesting() {
        synchronized (BadTerrainTileRegistry.class) {
            initializedGameDir = null;
            cachedSourceZoomByTargetTile = null;
        }
    }

    private static Map<TargetTile, Integer> requireSourceZoomByTargetTile() {
        Map<TargetTile, Integer> local = cachedSourceZoomByTargetTile;
        if (local != null) {
            return local;
        }
        synchronized (BadTerrainTileRegistry.class) {
            local = cachedSourceZoomByTargetTile;
            if (local == null) {
                local = loadSourceZoomByTargetTile(initializedGameDir);
                cachedSourceZoomByTargetTile = local;
                LOGGER.info(
                    "[TX-TERRAIN] bathymetry source-zoom override registry loaded entries={} external={}",
                    local.size(),
                    externalConfigPath(initializedGameDir)
                );
            }
            return local;
        }
    }

    private static Map<TargetTile, Integer> loadSourceZoomByTargetTile(Path gameDir) {
        Map<TargetTile, Integer> bundledMappings = loadBundledMappings();
        Path externalPath = externalConfigPath(gameDir);
        if (externalPath == null || !Files.isRegularFile(externalPath)) {
            return bundledMappings;
        }

        try (Reader reader = Files.newBufferedReader(externalPath, StandardCharsets.UTF_8)) {
            Map<TargetTile, Integer> externalMappings = parseMappingsGeoJson(reader, externalPath.toString());
            Map<TargetTile, Integer> mergedMappings = mergeMappings(bundledMappings, externalMappings);
            LOGGER.info(
                "[TX-TERRAIN] loaded external bathymetry source-zoom overrides path={} entries={} merged_entries={}",
                externalPath,
                externalMappings.size(),
                mergedMappings.size()
            );
            return mergedMappings;
        } catch (Exception exception) {
            LOGGER.warn(
                "[TX-TERRAIN] ignoring invalid external bathymetry override file path={} error={}",
                externalPath,
                exception.toString()
            );
            return bundledMappings;
        }
    }

    private static Map<TargetTile, Integer> loadBundledMappings() {
        InputStream stream = BadTerrainTileRegistry.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled bathymetry override resource: " + RESOURCE_PATH);
        }
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return parseMappingsGeoJson(reader, RESOURCE_PATH);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed loading bundled bathymetry override resource: " + RESOURCE_PATH, exception);
        }
    }

    private static Map<TargetTile, Integer> parseMappingsGeoJson(Reader reader, String sourceLabel) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(sourceLabel, "sourceLabel");

        try {
            JsonElement parsedRoot = JsonParser.parseReader(reader);
            JsonObject root = requireObject(parsedRoot, sourceLabel + " root");
            String rootType = requireString(root, "type", sourceLabel + " root");
            if (!"FeatureCollection".equals(rootType)) {
                throw new IllegalStateException(sourceLabel + " must declare GeoJSON type FeatureCollection");
            }

            JsonArray features = requireArray(root.get("features"), sourceLabel + " features");
            List<FeatureRule> rules = new ArrayList<>(features.size());
            for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
                String featureContext = sourceLabel + " feature[" + featureIndex + "]";
                JsonObject feature = requireObject(features.get(featureIndex), featureContext);
                String featureType = requireString(feature, "type", featureContext);
                if (!"Feature".equals(featureType)) {
                    throw new IllegalStateException(featureContext + " must declare GeoJSON type Feature");
                }

                JsonObject geometry = requireObject(feature.get("geometry"), featureContext + " geometry");
                JsonObject properties = requireObject(feature.get("properties"), featureContext + " properties");
                int sourceZoom = requireZoom(properties, "source_zoom", featureContext);
                int minTargetZoom = optionalZoom(properties, "min_target_zoom", DEFAULT_MIN_TARGET_ZOOM, featureContext);
                int maxTargetZoom = optionalZoom(properties, "max_target_zoom", EarthGenConfig.MAX_ZOOM, featureContext);

                if (minTargetZoom > maxTargetZoom) {
                    throw new IllegalStateException(
                        featureContext
                            + " has invalid zoom range min_target_zoom="
                            + minTargetZoom
                            + " max_target_zoom="
                            + maxTargetZoom
                    );
                }
                for (int targetZoom = minTargetZoom; targetZoom <= maxTargetZoom; targetZoom++) {
                    if (sourceZoom >= targetZoom) {
                        throw new IllegalStateException(
                            featureContext
                                + " requires source_zoom < target_zoom but got source_zoom="
                                + sourceZoom
                                + " target_zoom="
                                + targetZoom
                        );
                    }
                }

                List<ProjectedPolygon> polygons = parsePolygons(geometry, featureContext);
                rules.add(new FeatureRule(sourceZoom, minTargetZoom, maxTargetZoom, polygons));
            }
            return compileRules(rules);
        } catch (JsonParseException exception) {
            throw new IllegalStateException("Failed parsing GeoJSON from " + sourceLabel + ": " + exception.getMessage(), exception);
        } catch (ClassCastException | IllegalStateException exception) {
            if (exception instanceof IllegalStateException) {
                throw (IllegalStateException) exception;
            }
            throw new IllegalStateException("Invalid GeoJSON structure in " + sourceLabel, exception);
        }
    }

    private static Map<TargetTile, Integer> compileRules(List<FeatureRule> rules) {
        Map<TargetTile, Integer> mappings = new HashMap<>();
        for (FeatureRule rule : rules) {
            for (int targetZoom = rule.minTargetZoom(); targetZoom <= rule.maxTargetZoom(); targetZoom++) {
                int tileCount = EarthGenConfig.tileCountPerAxis(targetZoom);
                double tileSize = 1.0 / tileCount;
                for (ProjectedPolygon polygon : rule.polygons()) {
                    if (polygon.maxX() <= 0.0 || polygon.minX() >= 1.0 || polygon.maxY() <= 0.0 || polygon.minY() >= 1.0) {
                        continue;
                    }
                    int minTileX = tileIndexFromMin(Math.max(0.0, polygon.minX()), tileCount);
                    int maxTileX = tileIndexFromMax(Math.min(1.0, polygon.maxX()), tileCount);
                    int minTileY = tileIndexFromMin(Math.max(0.0, polygon.minY()), tileCount);
                    int maxTileY = tileIndexFromMax(Math.min(1.0, polygon.maxY()), tileCount);
                    if (maxTileX < minTileX || maxTileY < minTileY) {
                        continue;
                    }

                    for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                        double tileMinY = tileY * tileSize;
                        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                            double tileMinX = tileX * tileSize;
                            Rectangle2D.Double tileBounds = new Rectangle2D.Double(tileMinX, tileMinY, tileSize, tileSize);
                            Area intersection = new Area(polygon.path());
                            intersection.intersect(new Area(tileBounds));
                            if (intersection.isEmpty()) {
                                continue;
                            }
                            TargetTile target = new TargetTile(targetZoom, new TileKey(tileX, tileY));
                            mappings.merge(target, rule.sourceZoom(), Math::min);
                        }
                    }
                }
            }
        }
        return Map.copyOf(mappings);
    }

    static Map<TargetTile, Integer> mergeMappings(
        Map<TargetTile, Integer> bundledMappings,
        Map<TargetTile, Integer> externalMappings
    ) {
        if (externalMappings == null || externalMappings.isEmpty()) {
            return Map.copyOf(Objects.requireNonNull(bundledMappings, "bundledMappings"));
        }
        Map<TargetTile, Integer> mergedMappings = new HashMap<>(Objects.requireNonNull(bundledMappings, "bundledMappings"));
        for (Map.Entry<TargetTile, Integer> entry : externalMappings.entrySet()) {
            mergedMappings.merge(entry.getKey(), entry.getValue(), Math::min);
        }
        return Map.copyOf(mergedMappings);
    }

    private static List<ProjectedPolygon> parsePolygons(JsonObject geometry, String featureContext) {
        String geometryType = requireString(geometry, "type", featureContext + " geometry");
        JsonElement coordinates = geometry.get("coordinates");
        if ("Polygon".equals(geometryType)) {
            return List.of(parsePolygon(coordinates, featureContext + " polygon"));
        }
        if ("MultiPolygon".equals(geometryType)) {
            JsonArray polygonArray = requireArray(coordinates, featureContext + " multipolygon coordinates");
            if (polygonArray.isEmpty()) {
                throw new IllegalStateException(featureContext + " multipolygon must contain at least one polygon");
            }
            List<ProjectedPolygon> polygons = new ArrayList<>(polygonArray.size());
            for (int polygonIndex = 0; polygonIndex < polygonArray.size(); polygonIndex++) {
                polygons.add(parsePolygon(polygonArray.get(polygonIndex), featureContext + " multipolygon[" + polygonIndex + "]"));
            }
            return List.copyOf(polygons);
        }
        throw new IllegalStateException(featureContext + " geometry type " + geometryType + " is unsupported (expected Polygon or MultiPolygon)");
    }

    private static ProjectedPolygon parsePolygon(JsonElement polygonElement, String context) {
        JsonArray rings = requireArray(polygonElement, context + " coordinates");
        if (rings.isEmpty()) {
            throw new IllegalStateException(context + " must contain at least one ring");
        }

        ProjectedRing exterior = parseRing(rings.get(0), context + " exterior");
        List<ProjectedRing> holes = new ArrayList<>(Math.max(0, rings.size() - 1));
        for (int holeIndex = 1; holeIndex < rings.size(); holeIndex++) {
            holes.add(parseRing(rings.get(holeIndex), context + " hole[" + (holeIndex - 1) + "]"));
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        appendRing(path, exterior);
        for (ProjectedRing hole : holes) {
            appendRing(path, hole);
        }

        double minX = exterior.minX();
        double maxX = exterior.maxX();
        double minY = exterior.minY();
        double maxY = exterior.maxY();
        for (ProjectedRing hole : holes) {
            minX = Math.min(minX, hole.minX());
            maxX = Math.max(maxX, hole.maxX());
            minY = Math.min(minY, hole.minY());
            maxY = Math.max(maxY, hole.maxY());
        }
        return new ProjectedPolygon(path, minX, maxX, minY, maxY);
    }

    private static ProjectedRing parseRing(JsonElement ringElement, String context) {
        JsonArray coordinates = requireArray(ringElement, context + " coordinates");
        if (coordinates.size() < 4) {
            throw new IllegalStateException(context + " must contain at least four coordinate pairs");
        }

        List<Double> lons = new ArrayList<>(coordinates.size() + 1);
        List<Double> lats = new ArrayList<>(coordinates.size() + 1);
        for (int coordinateIndex = 0; coordinateIndex < coordinates.size(); coordinateIndex++) {
            JsonArray pair = requireArray(coordinates.get(coordinateIndex), context + " coordinate[" + coordinateIndex + "]");
            if (pair.size() < 2) {
                throw new IllegalStateException(context + " coordinate[" + coordinateIndex + "] must contain [longitude, latitude]");
            }
            double longitude = requireFiniteDouble(pair.get(0), context + " longitude[" + coordinateIndex + "]");
            double latitude = requireFiniteDouble(pair.get(1), context + " latitude[" + coordinateIndex + "]");
            validateLongitudeLatitude(longitude, latitude, context, coordinateIndex);
            lons.add(longitude);
            lats.add(latitude);
        }

        int lastIndex = lons.size() - 1;
        if (!sameCoordinate(lons.get(0), lats.get(0), lons.get(lastIndex), lats.get(lastIndex))) {
            lons.add(lons.get(0));
            lats.add(lats.get(0));
        }
        if (lons.size() < 4) {
            throw new IllegalStateException(context + " must contain at least four coordinate pairs after closure");
        }

        double[] lonArray = toDoubleArray(lons);
        double[] latArray = toDoubleArray(lats);
        validateNoAntimeridianCrossing(lonArray, context);

        double[] xs = new double[lonArray.length];
        double[] ys = new double[latArray.length];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < lonArray.length; i++) {
            xs[i] = projectLongitudeToUnit(lonArray[i]);
            ys[i] = projectLatitudeToUnit(latArray[i]);
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        if (Math.abs(signedArea(xs, ys)) < 1.0e-15) {
            throw new IllegalStateException(context + " has zero projected area");
        }
        return new ProjectedRing(xs, ys, minX, maxX, minY, maxY);
    }

    private static void appendRing(Path2D.Double path, ProjectedRing ring) {
        double[] xs = ring.xs();
        double[] ys = ring.ys();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        path.closePath();
    }

    private static double signedArea(double[] xs, double[] ys) {
        double area = 0.0;
        for (int i = 0; i < xs.length - 1; i++) {
            area += xs[i] * ys[i + 1] - xs[i + 1] * ys[i];
        }
        return area * 0.5;
    }

    private static int tileIndexFromMin(double minUnit, int tileCount) {
        return clampTileIndex((int) Math.floor(minUnit * tileCount), tileCount);
    }

    private static int tileIndexFromMax(double maxUnit, int tileCount) {
        if (maxUnit <= 0.0) {
            return -1;
        }
        double exclusiveMax = maxUnit >= 1.0 ? 1.0 : Math.nextDown(maxUnit);
        return clampTileIndex((int) Math.floor(exclusiveMax * tileCount), tileCount);
    }

    private static int clampTileIndex(int value, int tileCount) {
        if (value < 0) {
            return 0;
        }
        if (value >= tileCount) {
            return tileCount - 1;
        }
        return value;
    }

    private static int requireZoom(JsonObject object, String key, String context) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IllegalStateException(context + " missing required property " + key);
        }
        int zoom = requireInteger(element, context + " property " + key);
        return validateSupportedZoom(zoom, context + " property " + key);
    }

    private static int optionalZoom(JsonObject object, String key, int defaultValue, String context) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        int zoom = requireInteger(element, context + " property " + key);
        return validateSupportedZoom(zoom, context + " property " + key);
    }

    private static int validateSupportedZoom(int zoom, String context) {
        try {
            return EarthGenConfig.validateZoom(zoom);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                context
                    + " has unsupported zoom "
                    + zoom
                    + "; supported zooms are "
                    + EarthGenConfig.MIN_ZOOM
                    + "-"
                    + EarthGenConfig.MAX_ZOOM,
                exception
            );
        }
    }

    private static JsonObject requireObject(JsonElement element, String context) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new IllegalStateException(context + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String context) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new IllegalStateException(context + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String key, String context) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalStateException(context + " missing or invalid string field " + key);
        }
        return element.getAsString();
    }

    private static int requireInteger(JsonElement element, String context) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException(context + " must be an integer");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalStateException(context + " must be an integer");
        }
        return (int) value;
    }

    private static double requireFiniteDouble(JsonElement element, String context) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException(context + " must be numeric");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(context + " must be finite");
        }
        return value;
    }

    private static void validateLongitudeLatitude(double longitude, double latitude, String context, int coordinateIndex) {
        if (longitude < EarthGenConfig.MIN_LONGITUDE || longitude > EarthGenConfig.MAX_LONGITUDE) {
            throw new IllegalStateException(
                context
                    + " longitude["
                    + coordinateIndex
                    + "]="
                    + longitude
                    + " is outside supported range "
                    + EarthGenConfig.MIN_LONGITUDE
                    + ".."
                    + EarthGenConfig.MAX_LONGITUDE
            );
        }
        if (latitude < -EarthGenConfig.MAX_MERCATOR_LATITUDE || latitude > EarthGenConfig.MAX_MERCATOR_LATITUDE) {
            throw new IllegalStateException(
                context
                    + " latitude["
                    + coordinateIndex
                    + "]="
                    + latitude
                    + " is outside WebMercator range "
                    + (-EarthGenConfig.MAX_MERCATOR_LATITUDE)
                    + ".."
                    + EarthGenConfig.MAX_MERCATOR_LATITUDE
            );
        }
    }

    private static void validateNoAntimeridianCrossing(double[] longitudes, String context) {
        for (int i = 1; i < longitudes.length; i++) {
            double delta = Math.abs(longitudes[i] - longitudes[i - 1]);
            if (delta > 180.0 && delta < 360.0) {
                throw new IllegalStateException(context + " crosses the antimeridian; antimeridian polygons are unsupported");
            }
        }
    }

    private static boolean sameCoordinate(double lonA, double latA, double lonB, double latB) {
        return Double.doubleToLongBits(lonA) == Double.doubleToLongBits(lonB)
            && Double.doubleToLongBits(latA) == Double.doubleToLongBits(latB);
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    private static double projectLongitudeToUnit(double longitude) {
        return (longitude - EarthGenConfig.MIN_LONGITUDE) / 360.0;
    }

    private static double projectLatitudeToUnit(double latitude) {
        double latitudeRadians = Math.toRadians(latitude);
        return (1.0 - (Math.log(Math.tan(latitudeRadians) + (1.0 / Math.cos(latitudeRadians))) / Math.PI)) / 2.0;
    }

    private static Path normalizeGameDir(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        return gameDir.toAbsolutePath().normalize();
    }

    private static Path externalConfigPath(Path gameDir) {
        if (gameDir == null) {
            return null;
        }
        return gameDir.resolve("config").resolve(EXTERNAL_CONFIG_FILE_NAME);
    }

    public record TargetTile(int targetZoom, TileKey tileKey) {
        public TargetTile {
            targetZoom = EarthGenConfig.validateZoom(targetZoom);
            tileKey = java.util.Objects.requireNonNull(tileKey, "tileKey");
        }
    }

    private record FeatureRule(int sourceZoom, int minTargetZoom, int maxTargetZoom, List<ProjectedPolygon> polygons) {
    }

    private record ProjectedPolygon(Path2D.Double path, double minX, double maxX, double minY, double maxY) {
    }

    private record ProjectedRing(double[] xs, double[] ys, double minX, double maxX, double minY, double maxY) {
    }
}
