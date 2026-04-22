package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadTerrainTileRegistryTest {
    @AfterEach
    void tearDown() {
        BadTerrainTileRegistry.resetForTesting();
    }

    @Test
    void parseMappingsGeoJsonParsesPolygonAndMultiPolygon() {
        String geoJson = featureCollection(
            polygonFeature(9, 10, 10, tilePolygonCoordinates(10, 100, 200)),
            multiPolygonFeature(
                9,
                10,
                10,
                tilePolygonCoordinates(10, 101, 200),
                tilePolygonCoordinates(10, 102, 200)
            )
        );

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.parseMappingsGeoJson(new StringReader(geoJson));

        assertEquals(3, mappings.size());
        assertEquals(9, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(100, 200))));
        assertEquals(9, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(101, 200))));
        assertEquals(9, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(102, 200))));
    }

    @Test
    void parseMappingsGeoJsonAppliesDefaultZoomRange() {
        String geoJson = featureCollection(
            polygonFeature(8, null, null, tilePolygonCoordinates(9, 306, 200))
        );

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.parseMappingsGeoJson(new StringReader(geoJson));

        for (int zoom = 9; zoom <= EarthGenConfig.MAX_ZOOM; zoom++) {
            int currentZoom = zoom;
            boolean hasZoomEntry = mappings.keySet().stream().anyMatch(tile -> tile.targetZoom() == currentZoom);
            assertTrue(hasZoomEntry, "expected at least one mapping at target zoom " + zoom);
        }
        boolean hasUnsupportedZoom = mappings.keySet().stream().anyMatch(tile -> tile.targetZoom() < 9);
        assertTrue(!hasUnsupportedZoom);
    }

    @Test
    void parseMappingsGeoJsonRejectsInvalidZoomRelationship() {
        String geoJson = featureCollection(
            polygonFeature(9, 9, 9, tilePolygonCoordinates(9, 306, 200))
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> BadTerrainTileRegistry.parseMappingsGeoJson(new StringReader(geoJson))
        );
        assertTrue(exception.getMessage().contains("source_zoom < min_target_zoom"));
    }

    @Test
    void parseMappingsGeoJsonRejectsAntimeridianCrossing() {
        String geoJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "source_zoom": 8, "min_target_zoom": 9, "max_target_zoom": 9 },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [[179.0, 10.0], [-179.0, 10.0], [-179.0, 9.0], [179.0, 9.0], [179.0, 10.0]]
                    ]
                  }
                }
              ]
            }
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> BadTerrainTileRegistry.parseMappingsGeoJson(new StringReader(geoJson))
        );
        assertTrue(exception.getMessage().contains("antimeridian"));
    }

    @Test
    void parseMappingsGeoJsonOverlapUsesLowerSourceZoom() {
        String tilePolygon = tilePolygonCoordinates(10, 612, 400);
        String geoJson = featureCollection(
            polygonFeature(9, 10, 10, tilePolygon),
            polygonFeature(8, 10, 10, tilePolygon)
        );

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.parseMappingsGeoJson(new StringReader(geoJson));

        assertEquals(8, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(612, 400))));
    }

    @Test
    void loadMappingsForTestingSupportsBundledOnly() {
        String bundled = featureCollection(
            polygonFeature(8, 9, 9, tilePolygonCoordinates(9, 306, 200))
        );

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.loadMappingsForTesting(bundled, null);

        assertEquals(1, mappings.size());
        assertEquals(8, mappings.get(new BadTerrainTileRegistry.TargetTile(9, new TileKey(306, 200))));
    }

    @Test
    void loadMappingsForTestingMergesExternalRules() {
        String bundled = featureCollection(
            polygonFeature(9, 10, 10, tilePolygonCoordinates(10, 612, 400))
        );
        String external = featureCollection(
            polygonFeature(8, 10, 10, tilePolygonCoordinates(10, 612, 400)),
            polygonFeature(8, 10, 10, tilePolygonCoordinates(10, 613, 400))
        );

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.loadMappingsForTesting(bundled, external);

        assertEquals(2, mappings.size());
        assertEquals(8, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(612, 400))));
        assertEquals(8, mappings.get(new BadTerrainTileRegistry.TargetTile(10, new TileKey(613, 400))));
    }

    @Test
    void loadMappingsForTestingIgnoresInvalidExternalFile() {
        String bundled = featureCollection(
            polygonFeature(8, 9, 9, tilePolygonCoordinates(9, 306, 200))
        );
        String invalidExternal = "{ not valid json";

        Map<BadTerrainTileRegistry.TargetTile, Integer> mappings = BadTerrainTileRegistry.loadMappingsForTesting(bundled, invalidExternal);

        assertEquals(1, mappings.size());
        assertEquals(8, mappings.get(new BadTerrainTileRegistry.TargetTile(9, new TileKey(306, 200))));
    }

    @Test
    void loadMappingsForTestingRejectsInvalidBundledFile() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> BadTerrainTileRegistry.loadMappingsForTesting("{ not valid json", null)
        );
        assertTrue(exception.getMessage().contains("Failed parsing GeoJSON"));
    }

    @Test
    void startupValidationIsLazyAndResolvesKnownBundledOverrides(@TempDir Path tempDir) throws IOException {
        Path gameDir = prepareGameDir(tempDir);
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();

        assertEquals(0, BadTerrainTileRegistry.memoizedTargetTileCountForTesting());
        assertEquals(Set.of(8), BadTerrainTileRegistry.sourceZoomsForTargetZoom(9));
        assertEquals(Set.of(8), BadTerrainTileRegistry.sourceZoomsForTargetZoom(10));
        assertEquals(Set.of(8), BadTerrainTileRegistry.sourceZoomsForTargetZoom(11));

        OptionalInt sourceZoom = BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(306, 200));
        assertTrue(sourceZoom.isPresent());
        assertEquals(8, sourceZoom.orElseThrow());
        assertEquals(1, BadTerrainTileRegistry.memoizedTargetTileCountForTesting());
    }

    @Test
    void sourceZoomForMemoizesMisses(@TempDir Path tempDir) throws IOException {
        Path gameDir = prepareGameDir(tempDir);
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();

        assertTrue(BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(0, 0)).isEmpty());
        assertEquals(1, BadTerrainTileRegistry.memoizedTargetTileCountForTesting());
        assertTrue(BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(0, 0)).isEmpty());
        assertEquals(1, BadTerrainTileRegistry.memoizedTargetTileCountForTesting());
    }

    @Test
    void resolvedEntriesArePersistedAndLoadedFromDiskCache(@TempDir Path tempDir) throws IOException {
        Path gameDir = prepareGameDir(tempDir);
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        assertEquals(8, BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(306, 200)).orElseThrow());

        BadTerrainTileRegistry.flushCacheForTesting();
        Path cacheFile = BadTerrainTileRegistry.cacheFilePathForTesting();
        assertNotNull(cacheFile);
        assertTrue(Files.isRegularFile(cacheFile));

        BadTerrainTileRegistry.resetForTesting();
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        assertEquals(1, BadTerrainTileRegistry.memoizedTargetTileCountForTesting());
        assertEquals(8, BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(306, 200)).orElseThrow());
    }

    @Test
    void cacheKeyChangesWhenExternalGeoJsonChanges(@TempDir Path tempDir) throws IOException {
        Path gameDir = prepareGameDir(tempDir);
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        Path cacheFileWithoutExternal = BadTerrainTileRegistry.cacheFilePathForTesting();
        assertNotNull(cacheFileWithoutExternal);

        Path externalPath = gameDir.resolve("config").resolve(BadTerrainTileRegistry.EXTERNAL_CONFIG_FILE_NAME);
        Files.writeString(
            externalPath,
            featureCollection(polygonFeature(8, 9, 9, tilePolygonCoordinates(9, 320, 200))),
            StandardCharsets.UTF_8
        );

        BadTerrainTileRegistry.resetForTesting();
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        Path cacheFileWithExternal = BadTerrainTileRegistry.cacheFilePathForTesting();
        assertNotNull(cacheFileWithExternal);
        assertNotEquals(cacheFileWithoutExternal, cacheFileWithExternal);
    }

    @Test
    void corruptCacheFileFallsBackToLazyRecompute(@TempDir Path tempDir) throws IOException {
        Path gameDir = prepareGameDir(tempDir);
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        assertEquals(8, BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(306, 200)).orElseThrow());
        BadTerrainTileRegistry.flushCacheForTesting();
        Path cacheFile = BadTerrainTileRegistry.cacheFilePathForTesting();
        assertNotNull(cacheFile);

        Files.writeString(cacheFile, "corrupted-cache", StandardCharsets.UTF_8);
        BadTerrainTileRegistry.resetForTesting();
        BadTerrainTileRegistry.initialize(gameDir);
        BadTerrainTileRegistry.validateStartupRegistry();
        assertEquals(8, BadTerrainTileRegistry.sourceZoomFor(9, new TileKey(306, 200)).orElseThrow());
    }

    private static Path prepareGameDir(Path tempDir) throws IOException {
        Path gameDir = tempDir.resolve("game");
        Files.createDirectories(gameDir.resolve("config"));
        return gameDir;
    }

    private static String featureCollection(String... features) {
        return """
            {
              "type": "FeatureCollection",
              "features": [%s]
            }
            """.formatted(String.join(",", features));
    }

    private static String polygonFeature(Integer sourceZoom, Integer minTargetZoom, Integer maxTargetZoom, String polygonCoordinates) {
        return """
            {
              "type": "Feature",
              "properties": {%s},
              "geometry": {
                "type": "Polygon",
                "coordinates": %s
              }
            }
            """.formatted(propertiesJson(sourceZoom, minTargetZoom, maxTargetZoom), polygonCoordinates);
    }

    private static String multiPolygonFeature(Integer sourceZoom, Integer minTargetZoom, Integer maxTargetZoom, String... polygonCoordinates) {
        return """
            {
              "type": "Feature",
              "properties": {%s},
              "geometry": {
                "type": "MultiPolygon",
                "coordinates": [%s]
              }
            }
            """.formatted(propertiesJson(sourceZoom, minTargetZoom, maxTargetZoom), String.join(",", polygonCoordinates));
    }

    private static String propertiesJson(Integer sourceZoom, Integer minTargetZoom, Integer maxTargetZoom) {
        StringBuilder builder = new StringBuilder();
        builder.append("\"source_zoom\": ").append(sourceZoom);
        if (minTargetZoom != null) {
            builder.append(", \"min_target_zoom\": ").append(minTargetZoom);
        }
        if (maxTargetZoom != null) {
            builder.append(", \"max_target_zoom\": ").append(maxTargetZoom);
        }
        return builder.toString();
    }

    private static String tilePolygonCoordinates(int zoom, int tileX, int tileY) {
        double west = tileLongitude(zoom, tileX);
        double east = tileLongitude(zoom, tileX + 1);
        double north = tileNorthLatitude(zoom, tileY);
        double south = tileNorthLatitude(zoom, tileY + 1);
        double insetLongitude = (east - west) * 0.2;
        double insetLatitude = (north - south) * 0.2;
        double insetWest = west + insetLongitude;
        double insetEast = east - insetLongitude;
        double insetNorth = north - insetLatitude;
        double insetSouth = south + insetLatitude;
        return """
            [[[%.15f, %.15f], [%.15f, %.15f], [%.15f, %.15f], [%.15f, %.15f], [%.15f, %.15f]]]
            """.formatted(insetWest, insetNorth, insetEast, insetNorth, insetEast, insetSouth, insetWest, insetSouth, insetWest, insetNorth);
    }

    private static double tileLongitude(int zoom, int tileX) {
        return tileX * (360.0 / Math.scalb(1.0, EarthGenConfig.validateZoom(zoom))) - 180.0;
    }

    private static double tileNorthLatitude(int zoom, int tileY) {
        double normalizedY = tileY / Math.scalb(1.0, EarthGenConfig.validateZoom(zoom));
        double mercatorN = Math.PI * (1.0 - (2.0 * normalizedY));
        return Math.toDegrees(Math.atan(Math.sinh(mercatorN)));
    }
}
