package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.awt.geom.Path2D;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BadTerrainTileRegistry {
    static final String RESOURCE_PATH = "/data/terrarium_expanded/terrain/bad_tile_recovery.geojson";
    static final String EXTERNAL_CONFIG_FILE_NAME = "bad_tile_recovery.geojson";
    static final int DEFAULT_MIN_TARGET_ZOOM = 9;

    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static final int NO_SOURCE_ZOOM = -1;
    private static final int INDEX_BUCKETS_PER_AXIS = 64;
    private static final int CACHE_MAGIC = 0x54585247; // TXRG
    private static final int CACHE_SCHEMA_VERSION = 1;
    private static final String CACHE_FILE_PREFIX = "registry-v" + CACHE_SCHEMA_VERSION + "-";
    private static final String CACHE_FILE_SUFFIX = ".bin";
    private static final int CACHE_FLUSH_ENTRY_THRESHOLD = 256;
    private static final long CACHE_FLUSH_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static volatile RegistryRuntime runtime;
    private static volatile Path initializedGameDir;

    private BadTerrainTileRegistry() {
    }

    public static void initialize(Path gameDir) {
        Path normalizedGameDir = normalizeGameDir(gameDir);
        synchronized (BadTerrainTileRegistry.class) {
            if (Objects.equals(initializedGameDir, normalizedGameDir)) {
                return;
            }
            flushRuntimeLocked();
            initializedGameDir = normalizedGameDir;
            runtime = null;
        }
    }

    public static void shutdown() {
        synchronized (BadTerrainTileRegistry.class) {
            flushRuntimeLocked();
        }
    }

    public static void validateStartupRegistry() {
        requireRuntime();
    }

    public static OptionalInt sourceZoomFor(int targetZoom, TileKey targetTileKey) {
        int validatedZoom = EarthGenConfig.validateZoom(targetZoom);
        return requireRuntime().sourceZoomFor(validatedZoom, Objects.requireNonNull(targetTileKey, "targetTileKey"));
    }

    public static Set<Integer> sourceZoomsForTargetZoom(int targetZoom) {
        int validatedZoom = EarthGenConfig.validateZoom(targetZoom);
        return requireRuntime().sourceZoomsForTargetZoom(validatedZoom);
    }

    static Map<TargetTile, Integer> parseMappingsGeoJson(Reader reader) {
        return parseMappingsGeoJson(reader, "geojson");
    }

    static Map<TargetTile, Integer> loadMappingsForTesting(String bundledGeoJson, String externalGeoJson) {
        GeoJsonDefinition bundled = parseDefinition(
            new StringReader(Objects.requireNonNull(bundledGeoJson, "bundledGeoJson")),
            "bundled test geojson"
        );
        if (externalGeoJson == null || externalGeoJson.isBlank()) {
            return compileRules(bundled.rules());
        }
        try {
            GeoJsonDefinition external = parseDefinition(
                new StringReader(externalGeoJson),
                "external test geojson"
            );
            return compileRules(mergeRules(bundled.rules(), external.rules()));
        } catch (IllegalStateException exception) {
            return compileRules(bundled.rules());
        }
    }

    static void resetForTesting() {
        synchronized (BadTerrainTileRegistry.class) {
            flushRuntimeLocked();
            initializedGameDir = null;
            runtime = null;
        }
    }

    static int memoizedTargetTileCountForTesting() {
        RegistryRuntime local = runtime;
        return local == null ? 0 : local.memoizedTargetTileCount();
    }

    static Path cacheFilePathForTesting() {
        RegistryRuntime local = runtime;
        return local == null ? null : local.cacheFile();
    }

    static void flushCacheForTesting() {
        RegistryRuntime local = runtime;
        if (local != null) {
            local.flushResolvedCache(true);
        }
    }

    private static RegistryRuntime requireRuntime() {
        RegistryRuntime local = runtime;
        if (local != null) {
            return local;
        }
        synchronized (BadTerrainTileRegistry.class) {
            local = runtime;
            if (local == null) {
                local = loadRuntime(initializedGameDir);
                runtime = local;
            }
            return local;
        }
    }

    private static void flushRuntimeLocked() {
        RegistryRuntime local = runtime;
        runtime = null;
        if (local != null) {
            local.flushResolvedCache(true);
        }
    }

    private static RegistryRuntime loadRuntime(Path gameDir) {
        LoadedGeoJson bundled = loadBundledDefinition();
        LoadedGeoJson external = loadExternalDefinition(gameDir);
        List<FeatureRule> mergedRules = mergeRules(
            bundled.definition().rules(),
            external == null ? List.of() : external.definition().rules()
        );
        Map<Integer, ZoomIndex> zoomIndices = buildZoomIndices(mergedRules);
        String cacheKey = buildCacheKey(bundled.bytes(), external == null ? null : external.bytes());
        Path cacheFile = cacheFilePath(gameDir, cacheKey);
        Map<TargetTile, Integer> persistedResolvedEntries = readResolvedCache(cacheFile);
        RegistryRuntime built = new RegistryRuntime(zoomIndices, cacheFile, persistedResolvedEntries);
        LOGGER.info(
            "[TX-TERRAIN] bathymetry source-zoom override registry validated rules={} target_zooms={} memoized_entries={} external={} cache={}",
            mergedRules.size(),
            zoomIndices.keySet(),
            persistedResolvedEntries.size(),
            externalConfigPath(gameDir),
            cacheFile
        );
        return built;
    }

    private static LoadedGeoJson loadBundledDefinition() {
        InputStream stream = BadTerrainTileRegistry.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing bundled bathymetry override resource: " + RESOURCE_PATH);
        }
        try (InputStream input = stream) {
            byte[] bytes = input.readAllBytes();
            GeoJsonDefinition definition = parseDefinition(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8),
                RESOURCE_PATH
            );
            return new LoadedGeoJson(bytes, definition);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed loading bundled bathymetry override resource: " + RESOURCE_PATH, exception);
        }
    }

    private static LoadedGeoJson loadExternalDefinition(Path gameDir) {
        Path externalPath = externalConfigPath(gameDir);
        if (externalPath == null || !Files.isRegularFile(externalPath)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(externalPath);
            GeoJsonDefinition definition = parseDefinition(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8),
                externalPath.toString()
            );
            LOGGER.info(
                "[TX-TERRAIN] loaded external bathymetry source-zoom overrides path={} rules={}",
                externalPath,
                definition.rules().size()
            );
            return new LoadedGeoJson(bytes, definition);
        } catch (Exception exception) {
            LOGGER.warn(
                "[TX-TERRAIN] ignoring invalid external bathymetry override file path={} error={}",
                externalPath,
                exception.toString()
            );
            return null;
        }
    }

    private static List<FeatureRule> mergeRules(List<FeatureRule> bundledRules, List<FeatureRule> externalRules) {
        List<FeatureRule> merged = new ArrayList<>(bundledRules.size() + externalRules.size());
        merged.addAll(bundledRules);
        merged.addAll(externalRules);
        return List.copyOf(merged);
    }

    private static Map<Integer, ZoomIndex> buildZoomIndices(List<FeatureRule> rules) {
        Map<Integer, List<PolygonRule>> polygonsByTargetZoom = new LinkedHashMap<>();
        for (FeatureRule rule : rules) {
            for (int targetZoom = rule.minTargetZoom(); targetZoom <= rule.maxTargetZoom(); targetZoom++) {
                List<PolygonRule> polygons = polygonsByTargetZoom.computeIfAbsent(targetZoom, ignored -> new ArrayList<>());
                for (ProjectedPolygon polygon : rule.polygons()) {
                    if (polygon.maxX() <= 0.0 || polygon.minX() >= 1.0 || polygon.maxY() <= 0.0 || polygon.minY() >= 1.0) {
                        continue;
                    }
                    polygons.add(new PolygonRule(rule.sourceZoom(), polygon.path(), polygon.minX(), polygon.maxX(), polygon.minY(), polygon.maxY()));
                }
            }
        }

        Map<Integer, ZoomIndex> zoomIndices = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<PolygonRule>> entry : polygonsByTargetZoom.entrySet()) {
            zoomIndices.put(entry.getKey(), ZoomIndex.create(entry.getKey(), entry.getValue()));
        }
        return Map.copyOf(zoomIndices);
    }

    private static String buildCacheKey(byte[] bundledBytes, byte[] externalBytes) {
        MessageDigest digest = newSha256Digest();
        digest.update(new byte[] { (byte) CACHE_SCHEMA_VERSION });
        updateDigestWithBytes(digest, bundledBytes);
        if (externalBytes != null) {
            digest.update((byte) 1);
            updateDigestWithBytes(digest, externalBytes);
        } else {
            digest.update((byte) 0);
        }
        byte[] hash = digest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static void updateDigestWithBytes(MessageDigest digest, byte[] bytes) {
        byte[] lengthPrefix = ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array();
        digest.update(lengthPrefix);
        digest.update(bytes);
    }

    private static Map<TargetTile, Integer> readResolvedCache(Path cacheFile) {
        if (cacheFile == null || !Files.isRegularFile(cacheFile)) {
            return Map.of();
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(cacheFile)))) {
            int magic = input.readInt();
            if (magic != CACHE_MAGIC) {
                throw new IllegalStateException("unexpected cache magic");
            }
            int schemaVersion = input.readInt();
            if (schemaVersion != CACHE_SCHEMA_VERSION) {
                throw new IllegalStateException("unexpected cache schema version");
            }
            int entryCount = input.readInt();
            if (entryCount < 0) {
                throw new IllegalStateException("negative cache entry count");
            }
            Map<TargetTile, Integer> loaded = new HashMap<>(Math.max(16, entryCount));
            for (int index = 0; index < entryCount; index++) {
                int targetZoom = input.readInt();
                int tileX = input.readInt();
                int tileY = input.readInt();
                int sourceZoom = input.readInt();
                if (!EarthGenConfig.isSupportedZoom(targetZoom) || !EarthGenConfig.isSupportedZoom(sourceZoom)) {
                    continue;
                }
                if (sourceZoom >= targetZoom) {
                    continue;
                }
                TargetTile target = new TargetTile(targetZoom, new TileKey(tileX, tileY));
                loaded.merge(target, sourceZoom, Math::min);
            }
            return Map.copyOf(loaded);
        } catch (Exception exception) {
            LOGGER.warn(
                "[TX-TERRAIN] ignoring invalid bad terrain cache file path={} error={}",
                cacheFile,
                exception.toString()
            );
            return Map.of();
        }
    }

    private static void writeResolvedCache(Path cacheFile, Map<TargetTile, Integer> resolvedHits) throws IOException {
        if (cacheFile == null) {
            return;
        }
        Files.createDirectories(cacheFile.getParent());
        cleanupStaleCacheFiles(cacheFile.getParent(), cacheFile.getFileName().toString());

        Path tempFile = cacheFile.resolveSibling(cacheFile.getFileName() + ".part");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            output.writeInt(CACHE_MAGIC);
            output.writeInt(CACHE_SCHEMA_VERSION);
            output.writeInt(resolvedHits.size());
            for (Map.Entry<TargetTile, Integer> entry : resolvedHits.entrySet()) {
                TargetTile target = entry.getKey();
                output.writeInt(target.targetZoom());
                output.writeInt(target.tileKey().x());
                output.writeInt(target.tileKey().y());
                output.writeInt(entry.getValue());
            }
        }
        try {
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupStaleCacheFiles(Path cacheDirectory, String activeFileName) {
        try {
            if (!Files.isDirectory(cacheDirectory)) {
                return;
            }
            try (var stream = Files.list(cacheDirectory)) {
                stream
                    .filter(path -> path.getFileName().toString().startsWith(CACHE_FILE_PREFIX))
                    .filter(path -> !path.getFileName().toString().equals(activeFileName))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
            }
        } catch (IOException ignored) {
        }
    }

    private static Path cacheFilePath(Path gameDir, String cacheKey) {
        if (gameDir == null) {
            return null;
        }
        Path cacheDirectory = gameDir.resolve(Path.of("cache", "terrarium_expanded", "terrain", "bad_tile_registry"));
        return cacheDirectory.resolve(CACHE_FILE_PREFIX + cacheKey + CACHE_FILE_SUFFIX);
    }

    private static GeoJsonDefinition parseDefinition(Reader reader, String sourceLabel) {
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
                if (sourceZoom >= minTargetZoom) {
                    throw new IllegalStateException(
                        featureContext
                            + " requires source_zoom < min_target_zoom but got source_zoom="
                            + sourceZoom
                            + " min_target_zoom="
                            + minTargetZoom
                    );
                }

                List<ProjectedPolygon> polygons = parsePolygons(geometry, featureContext);
                rules.add(new FeatureRule(sourceZoom, minTargetZoom, maxTargetZoom, polygons));
            }
            return new GeoJsonDefinition(List.copyOf(rules));
        } catch (JsonParseException exception) {
            throw new IllegalStateException("Failed parsing GeoJSON from " + sourceLabel + ": " + exception.getMessage(), exception);
        } catch (ClassCastException | IllegalStateException exception) {
            if (exception instanceof IllegalStateException) {
                throw (IllegalStateException) exception;
            }
            throw new IllegalStateException("Invalid GeoJSON structure in " + sourceLabel, exception);
        }
    }

    private static Map<TargetTile, Integer> parseMappingsGeoJson(Reader reader, String sourceLabel) {
        return compileRules(parseDefinition(reader, sourceLabel).rules());
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
                            if (!polygon.path().intersects(tileMinX, tileMinY, tileSize, tileSize)) {
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
            tileKey = Objects.requireNonNull(tileKey, "tileKey");
        }
    }

    private record GeoJsonDefinition(List<FeatureRule> rules) {
    }

    private record LoadedGeoJson(byte[] bytes, GeoJsonDefinition definition) {
    }

    private record FeatureRule(int sourceZoom, int minTargetZoom, int maxTargetZoom, List<ProjectedPolygon> polygons) {
    }

    private record ProjectedPolygon(Path2D.Double path, double minX, double maxX, double minY, double maxY) {
    }

    private record ProjectedRing(double[] xs, double[] ys, double minX, double maxX, double minY, double maxY) {
    }

    private record PolygonRule(int sourceZoom, Path2D.Double path, double minX, double maxX, double minY, double maxY) {
    }

    private static final class ZoomIndex {
        private final int targetZoom;
        private final int tileCount;
        private final double tileSize;
        private final int bucketCountPerAxis;
        private final double bucketSize;
        private final List<PolygonRule> polygonsById;
        private final Map<Integer, int[]> bucketToPolygonIds;
        private final Set<Integer> sourceZooms;

        private ZoomIndex(
            int targetZoom,
            int tileCount,
            double tileSize,
            int bucketCountPerAxis,
            double bucketSize,
            List<PolygonRule> polygonsById,
            Map<Integer, int[]> bucketToPolygonIds,
            Set<Integer> sourceZooms
        ) {
            this.targetZoom = targetZoom;
            this.tileCount = tileCount;
            this.tileSize = tileSize;
            this.bucketCountPerAxis = bucketCountPerAxis;
            this.bucketSize = bucketSize;
            this.polygonsById = polygonsById;
            this.bucketToPolygonIds = bucketToPolygonIds;
            this.sourceZooms = sourceZooms;
        }

        static ZoomIndex create(int targetZoom, List<PolygonRule> polygons) {
            int tileCount = EarthGenConfig.tileCountPerAxis(targetZoom);
            int bucketCount = Math.max(1, Math.min(INDEX_BUCKETS_PER_AXIS, tileCount));
            double bucketSize = 1.0 / bucketCount;
            List<PolygonRule> indexedPolygons = new ArrayList<>(polygons);
            Map<Integer, List<Integer>> bucketLists = new HashMap<>();
            Set<Integer> sourceZooms = new TreeSet<>();

            for (int polygonId = 0; polygonId < indexedPolygons.size(); polygonId++) {
                PolygonRule polygon = indexedPolygons.get(polygonId);
                sourceZooms.add(polygon.sourceZoom());
                int minBucketX = bucketIndexFromMin(Math.max(0.0, polygon.minX()), bucketCount);
                int maxBucketX = bucketIndexFromMax(Math.min(1.0, polygon.maxX()), bucketCount);
                int minBucketY = bucketIndexFromMin(Math.max(0.0, polygon.minY()), bucketCount);
                int maxBucketY = bucketIndexFromMax(Math.min(1.0, polygon.maxY()), bucketCount);
                if (maxBucketX < minBucketX || maxBucketY < minBucketY) {
                    continue;
                }
                for (int bucketY = minBucketY; bucketY <= maxBucketY; bucketY++) {
                    for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                        int bucketId = bucketId(bucketX, bucketY, bucketCount);
                        bucketLists.computeIfAbsent(bucketId, ignored -> new ArrayList<>()).add(polygonId);
                    }
                }
            }

            Map<Integer, int[]> bucketToPolygonIds = new HashMap<>(bucketLists.size());
            for (Map.Entry<Integer, List<Integer>> entry : bucketLists.entrySet()) {
                List<Integer> ids = entry.getValue();
                int[] array = new int[ids.size()];
                for (int index = 0; index < ids.size(); index++) {
                    array[index] = ids.get(index);
                }
                bucketToPolygonIds.put(entry.getKey(), array);
            }

            return new ZoomIndex(
                targetZoom,
                tileCount,
                1.0 / tileCount,
                bucketCount,
                bucketSize,
                List.copyOf(indexedPolygons),
                Map.copyOf(bucketToPolygonIds),
                Set.copyOf(sourceZooms)
            );
        }

        Set<Integer> sourceZooms() {
            return sourceZooms;
        }

        int resolveSourceZoom(TileKey tileKey) {
            if (tileKey.x() < 0 || tileKey.x() >= tileCount || tileKey.y() < 0 || tileKey.y() >= tileCount) {
                return NO_SOURCE_ZOOM;
            }

            double tileMinX = tileKey.x() * tileSize;
            double tileMinY = tileKey.y() * tileSize;
            double tileMaxX = tileMinX + tileSize;
            double tileMaxY = tileMinY + tileSize;

            int minBucketX = bucketIndexFromMin(tileMinX, bucketCountPerAxis);
            int maxBucketX = bucketIndexFromMax(tileMaxX, bucketCountPerAxis);
            int minBucketY = bucketIndexFromMin(tileMinY, bucketCountPerAxis);
            int maxBucketY = bucketIndexFromMax(tileMaxY, bucketCountPerAxis);
            if (maxBucketX < minBucketX || maxBucketY < minBucketY) {
                return NO_SOURCE_ZOOM;
            }

            int bestSourceZoom = NO_SOURCE_ZOOM;
            Set<Integer> seenPolygonIds = new HashSet<>(32);
            for (int bucketY = minBucketY; bucketY <= maxBucketY; bucketY++) {
                for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                    int bucketId = bucketId(bucketX, bucketY, bucketCountPerAxis);
                    int[] polygonIds = bucketToPolygonIds.get(bucketId);
                    if (polygonIds == null) {
                        continue;
                    }
                    for (int polygonId : polygonIds) {
                        if (!seenPolygonIds.add(polygonId)) {
                            continue;
                        }
                        PolygonRule polygon = polygonsById.get(polygonId);
                        if (tileMaxX <= polygon.minX() || tileMinX >= polygon.maxX() || tileMaxY <= polygon.minY() || tileMinY >= polygon.maxY()) {
                            continue;
                        }
                        if (!polygon.path().intersects(tileMinX, tileMinY, tileSize, tileSize)) {
                            continue;
                        }
                        if (bestSourceZoom == NO_SOURCE_ZOOM || polygon.sourceZoom() < bestSourceZoom) {
                            bestSourceZoom = polygon.sourceZoom();
                            if (bestSourceZoom == EarthGenConfig.MIN_ZOOM) {
                                return bestSourceZoom;
                            }
                        }
                    }
                }
            }
            return bestSourceZoom;
        }

        private static int bucketId(int bucketX, int bucketY, int bucketCountPerAxis) {
            return bucketY * bucketCountPerAxis + bucketX;
        }

        private static int bucketIndexFromMin(double unit, int bucketCountPerAxis) {
            int value = (int) Math.floor(unit / (1.0 / bucketCountPerAxis));
            return clampBucketIndex(value, bucketCountPerAxis);
        }

        private static int bucketIndexFromMax(double unit, int bucketCountPerAxis) {
            if (unit <= 0.0) {
                return -1;
            }
            double exclusiveMax = unit >= 1.0 ? 1.0 : Math.nextDown(unit);
            int value = (int) Math.floor(exclusiveMax / (1.0 / bucketCountPerAxis));
            return clampBucketIndex(value, bucketCountPerAxis);
        }

        private static int clampBucketIndex(int value, int bucketCountPerAxis) {
            if (value < 0) {
                return 0;
            }
            if (value >= bucketCountPerAxis) {
                return bucketCountPerAxis - 1;
            }
            return value;
        }
    }

    private static final class RegistryRuntime {
        private final Map<Integer, ZoomIndex> zoomIndices;
        private final Map<Integer, Set<Integer>> sourceZoomsByTargetZoom;
        private final ConcurrentMap<TargetTile, Integer> memoizedSourceZoomByTargetTile;
        private final Path cacheFile;
        private final Object flushLock = new Object();
        private volatile long lastFlushNanos = System.nanoTime();
        private volatile int dirtyResolvedEntries;

        private RegistryRuntime(Map<Integer, ZoomIndex> zoomIndices, Path cacheFile, Map<TargetTile, Integer> persistedResolvedEntries) {
            this.zoomIndices = zoomIndices;
            this.sourceZoomsByTargetZoom = collectSourceZooms(zoomIndices);
            this.memoizedSourceZoomByTargetTile = new ConcurrentHashMap<>();
            this.memoizedSourceZoomByTargetTile.putAll(persistedResolvedEntries);
            this.cacheFile = cacheFile;
        }

        OptionalInt sourceZoomFor(int targetZoom, TileKey tileKey) {
            TargetTile target = new TargetTile(targetZoom, tileKey);
            Integer cached = memoizedSourceZoomByTargetTile.get(target);
            if (cached != null) {
                return toOptionalInt(cached);
            }

            ZoomIndex zoomIndex = zoomIndices.get(targetZoom);
            int resolved = zoomIndex == null ? NO_SOURCE_ZOOM : zoomIndex.resolveSourceZoom(tileKey);
            Integer existing = memoizedSourceZoomByTargetTile.putIfAbsent(target, resolved);
            int effective = existing == null ? resolved : existing;
            if (existing == null && effective != NO_SOURCE_ZOOM) {
                dirtyResolvedEntries++;
                maybeFlushResolvedCache();
            }
            return toOptionalInt(effective);
        }

        Set<Integer> sourceZoomsForTargetZoom(int targetZoom) {
            Set<Integer> sourceZooms = sourceZoomsByTargetZoom.get(targetZoom);
            if (sourceZooms == null) {
                return Set.of();
            }
            return sourceZooms;
        }

        int memoizedTargetTileCount() {
            return memoizedSourceZoomByTargetTile.size();
        }

        Path cacheFile() {
            return cacheFile;
        }

        void maybeFlushResolvedCache() {
            if (cacheFile == null) {
                return;
            }
            int dirtyCount = dirtyResolvedEntries;
            if (dirtyCount <= 0) {
                return;
            }
            long now = System.nanoTime();
            if (dirtyCount < CACHE_FLUSH_ENTRY_THRESHOLD && now - lastFlushNanos < CACHE_FLUSH_INTERVAL_NANOS) {
                return;
            }
            flushResolvedCache(false);
        }

        void flushResolvedCache(boolean force) {
            if (cacheFile == null) {
                return;
            }
            synchronized (flushLock) {
                if (!force && dirtyResolvedEntries <= 0) {
                    return;
                }
                Map<TargetTile, Integer> resolvedHits = new LinkedHashMap<>();
                for (Map.Entry<TargetTile, Integer> entry : memoizedSourceZoomByTargetTile.entrySet()) {
                    if (entry.getValue() == null || entry.getValue() == NO_SOURCE_ZOOM) {
                        continue;
                    }
                    resolvedHits.put(entry.getKey(), entry.getValue());
                }
                try {
                    writeResolvedCache(cacheFile, resolvedHits);
                    dirtyResolvedEntries = 0;
                    lastFlushNanos = System.nanoTime();
                } catch (IOException exception) {
                    LOGGER.warn(
                        "[TX-TERRAIN] failed writing bad terrain cache path={} error={}",
                        cacheFile,
                        exception.toString()
                    );
                }
            }
        }

        private static OptionalInt toOptionalInt(int sourceZoom) {
            return sourceZoom == NO_SOURCE_ZOOM ? OptionalInt.empty() : OptionalInt.of(sourceZoom);
        }

        private static Map<Integer, Set<Integer>> collectSourceZooms(Map<Integer, ZoomIndex> zoomIndices) {
            Map<Integer, Set<Integer>> sourceZooms = new HashMap<>();
            for (Map.Entry<Integer, ZoomIndex> entry : zoomIndices.entrySet()) {
                sourceZooms.put(entry.getKey(), entry.getValue().sourceZooms());
            }
            return Collections.unmodifiableMap(sourceZooms);
        }
    }
}
