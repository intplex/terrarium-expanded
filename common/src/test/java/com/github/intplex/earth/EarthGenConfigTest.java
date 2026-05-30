package com.github.intplex.earth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthGenConfigTest {
    @AfterEach
    void restoreActiveDefaults() {
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
        EarthGenConfig.setActiveMaxTerrainY(EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y);
        EarthGenConfig.resetActiveTerrainProfile();
    }

    @Test
    void supportedZoomValidationAcceptsExpectedRange() {
        assertDoesNotThrow(() -> EarthGenConfig.validateZoom(8));
        assertDoesNotThrow(() -> EarthGenConfig.validateZoom(9));
        assertDoesNotThrow(() -> EarthGenConfig.validateZoom(10));
        assertDoesNotThrow(() -> EarthGenConfig.validateZoom(11));
        assertDoesNotThrow(() -> EarthGenConfig.validateZoom(12));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateZoom(7));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateZoom(13));
    }

    @Test
    void spanAndTileCountsScaleWithZoom() {
        assertEquals(65_536, EarthGenConfig.blockSpanForZoom(8));
        assertEquals(131_072, EarthGenConfig.blockSpanForZoom(9));
        assertEquals(1_048_576, EarthGenConfig.blockSpanForZoom(12));
        assertEquals(256, EarthGenConfig.tileCountPerAxis(8));
        assertEquals(512, EarthGenConfig.tileCountPerAxis(9));
        assertEquals(4096, EarthGenConfig.tileCountPerAxis(12));
    }

    @Test
    void terrainProjectionMatchesRegularProjectionAtAllSupportedZooms() {
        int[] blockOffsets = {0, 1, 2, 3, 127, 255};
        for (int zoom = EarthGenConfig.MIN_ZOOM; zoom <= EarthGenConfig.MAX_ZOOM; zoom++) {
            int halfSpan = EarthGenConfig.halfSpanForZoom(zoom);
            for (int offset : blockOffsets) {
                Optional<EarthGenConfig.TileSamplePoint> terrain =
                    EarthGenConfig.projectBlockToTerrainTile(-halfSpan + offset, -halfSpan + offset, zoom);
                Optional<EarthGenConfig.TileSamplePoint> regular =
                    EarthGenConfig.projectBlockToTile(-halfSpan + offset, -halfSpan + offset, zoom);
                assertEquals(regular, terrain);
            }
        }
    }

    @Test
    void zeroAndLowPositiveMetersStayFlushWithSeaLevelCoastline() {
        assertEquals(EarthGenConfig.DEFAULT_SEA_LEVEL, EarthGenConfig.activeSeaLevel());
        assertEquals(EarthGenConfig.DEFAULT_SEA_LEVEL - 1, EarthGenConfig.mapMetersToTerrainY(0.0));
        assertEquals(EarthGenConfig.DEFAULT_SEA_LEVEL - 1, EarthGenConfig.mapMetersToTerrainY(1.0));
        assertEquals(EarthGenConfig.DEFAULT_SEA_LEVEL - 1, EarthGenConfig.mapMetersToTerrainY(30.0));
        assertTrue(EarthGenConfig.mapMetersToTerrainY(60.0) >= EarthGenConfig.DEFAULT_SEA_LEVEL - 1);
    }

    @Test
    void negativeMetersRemainBelowSeaLevel() {
        assertTrue(EarthGenConfig.mapMetersToTerrainY(-1.0) < EarthGenConfig.activeSeaLevel());
        assertTrue(EarthGenConfig.mapMetersToTerrainY(-1000.0) < EarthGenConfig.activeSeaLevel());
    }

    @Test
    void ecoregionProjectionMapsCornersToExpectedReducedTiles() {
        int halfSpan = EarthGenConfig.halfSpanForZoom(8);
        var min = EarthGenConfig.projectBlockToEcoregionTile(-halfSpan, -halfSpan, 8).orElseThrow();
        assertEquals(0, min.tileKey().x());
        assertEquals(0, min.tileKey().y());
        assertEquals(0, min.pixelX());
        assertEquals(0, min.pixelY());

        var max = EarthGenConfig.projectBlockToEcoregionTile(halfSpan - 1, halfSpan - 1, 8).orElseThrow();
        assertEquals(EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS - 1, max.tileKey().x());
        assertEquals(EarthGenConfig.ECOREGION_REDUCED_TILE_COUNT_PER_AXIS - 1, max.tileKey().y());
        assertEquals(EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE - 1, max.pixelX());
        assertEquals(EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE - 1, max.pixelY());
    }

    @Test
    void ecoregionProjectionResetsLocalPixelAtTileBoundary() {
        int boundary = -EarthGenConfig.halfSpanForZoom(8) + EarthGenConfig.ECOREGION_REDUCED_TILE_SIZE;
        var sample = EarthGenConfig.projectBlockToEcoregionTile(boundary, boundary, 8).orElseThrow();
        assertEquals(1, sample.tileKey().x());
        assertEquals(1, sample.tileKey().y());
        assertEquals(0, sample.pixelX());
        assertEquals(0, sample.pixelY());
    }

    @Test
    void ecoregionProjectionRejectsOutOfBoundsCoordinates() {
        int halfSpan = EarthGenConfig.halfSpanForZoom(8);
        assertFalse(EarthGenConfig.projectBlockToEcoregionTile(-halfSpan - 1, 0, 8).isPresent());
        assertFalse(EarthGenConfig.projectBlockToEcoregionTile(0, halfSpan, 8).isPresent());
    }

    @Test
    void ecoregionProjectionUsesNearestNeighborAtHigherZoom() {
        int halfSpanZoom9 = EarthGenConfig.halfSpanForZoom(9);
        int halfSpanZoom8 = EarthGenConfig.halfSpanForZoom(8);
        int[] blockOffsets = {0, 1, 2, 3, 127, 255};
        for (int offset : blockOffsets) {
            var zoom9 = EarthGenConfig.projectBlockToEcoregionTile(-halfSpanZoom9 + offset, -halfSpanZoom9 + offset, 9).orElseThrow();
            int downsampledOffset = offset / 2;
            var zoom8 = EarthGenConfig.projectBlockToEcoregionTile(-halfSpanZoom8 + downsampledOffset, -halfSpanZoom8 + downsampledOffset, 8).orElseThrow();

            assertEquals(zoom8.tileKey(), zoom9.tileKey());
            assertEquals(zoom8.pixelX(), zoom9.pixelX());
            assertEquals(zoom8.pixelY(), zoom9.pixelY());
        }
    }

    @Test
    void projectionApisMatchPresenceAcrossLayers() {
        int[] testCoords = {-2048, -1, 0, 1, 2048};
        for (int zoom = EarthGenConfig.MIN_ZOOM; zoom <= EarthGenConfig.MAX_ZOOM; zoom++) {
            for (int x : testCoords) {
                for (int z : testCoords) {
                    boolean regularProjected = EarthGenConfig.projectBlockToTile(x, z, zoom).isPresent();
                    boolean terrainProjected = EarthGenConfig.projectBlockToTerrainTile(x, z, zoom).isPresent();
                    boolean ecoregionProjected = EarthGenConfig.projectBlockToEcoregionTile(x, z, zoom).isPresent();

                    assertEquals(regularProjected, terrainProjected);
                    assertTrue(regularProjected || !ecoregionProjected);
                }
            }
        }
    }

    @Test
    void slippyProjectionMatchesReferenceFormulaForKnownCoordinate() {
        int zoom = 12;
        double latitude = 35.6590699;
        double longitude = 139.7006793;
        int n = 1 << zoom;
        double xtileExact = n * ((longitude + 180.0) / 360.0);
        double latRad = Math.toRadians(latitude);
        double ytileExact = n * (1.0 - (Math.log(Math.tan(latRad) + (1.0 / Math.cos(latRad))) / Math.PI)) / 2.0;
        int expectedTileX = (int) Math.floor(xtileExact);
        int expectedTileY = (int) Math.floor(ytileExact);
        int expectedPixelX = (int) Math.floor((xtileExact - expectedTileX) * EarthGenConfig.TILE_SIZE);
        int expectedPixelY = (int) Math.floor((ytileExact - expectedTileY) * EarthGenConfig.TILE_SIZE);

        var block = EarthGenConfig.geoToBlock(latitude, longitude, zoom).orElseThrow();
        var sample = EarthGenConfig.projectBlockToTile(block.x(), block.z(), zoom).orElseThrow();

        assertEquals(expectedTileX, sample.tileKey().x());
        assertEquals(expectedTileY, sample.tileKey().y());
        assertEquals(expectedPixelX, sample.pixelX());
        assertEquals(expectedPixelY, sample.pixelY());
    }

    @Test
    void geoRoundTripWorksAcrossZoomLevels() {
        for (int zoom = EarthGenConfig.MIN_ZOOM; zoom <= EarthGenConfig.MAX_ZOOM; zoom++) {
            double latitude = 43.6532;
            double longitude = -79.3832;
            var block = EarthGenConfig.geoToBlock(latitude, longitude, zoom).orElseThrow();
            var geo = EarthGenConfig.blockToGeo(block.x(), block.z(), zoom).orElseThrow();

            assertTrue(Math.abs(geo.latitude() - latitude) < 0.01);
            assertTrue(Math.abs(geo.longitude() - longitude) < 0.01);
        }
    }

    @Test
    void metersPerBlockDecreasesAsZoomIncreases() {
        double at8 = EarthGenConfig.metersPerBlockForZoom(8);
        double at12 = EarthGenConfig.metersPerBlockForZoom(12);
        assertTrue(at8 > at12);
        assertTrue(at8 > 600.0 && at8 < 620.0);
        assertTrue(at12 > 38.0 && at12 < 39.0);
    }

    @Test
    void terrainProfileSettingsAffectHeightMapping() {
        int defaultLand = EarthGenConfig.mapMetersToTerrainY(500.0);
        int defaultOcean = EarthGenConfig.mapMetersToTerrainY(-500.0);

        EarthGenConfig.setActiveTerrainProfile(128, 30);
        int lowerMountainLand = EarthGenConfig.mapMetersToTerrainY(500.0);
        int higherOceanFloorOcean = EarthGenConfig.mapMetersToTerrainY(-500.0);

        assertTrue(lowerMountainLand < defaultLand);
        assertTrue(higherOceanFloorOcean > defaultOcean);
    }

    @Test
    void terrainProfileSeaLevelShiftsHeightMapping() {
        EarthGenConfig.setActiveTerrainProfile(180, 20, 80);

        assertEquals(80, EarthGenConfig.activeSeaLevel());
        assertEquals(79, EarthGenConfig.mapMetersToTerrainY(0.0));
        assertTrue(EarthGenConfig.mapMetersToTerrainY(-1.0) < 80);
        assertTrue(EarthGenConfig.mapMetersToTerrainY(500.0) > 79);
    }

    @Test
    void terrainProfileValidationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateMaxMountainY(EarthGenConfig.DEFAULT_SEA_LEVEL));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateMaxMountainY(EarthGenConfig.DEFAULT_MAX_MOUNTAIN_Y + 1));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateOceanFloorY(EarthGenConfig.MIN_TERRAIN_Y - 1));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateOceanFloorY(EarthGenConfig.DEFAULT_SEA_LEVEL));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateSeaLevel(20, 100, 20));
        assertThrows(IllegalArgumentException.class, () -> EarthGenConfig.validateSeaLevel(100, 100, 20));
        assertDoesNotThrow(() -> EarthGenConfig.validateMaxMountainY(128));
        assertDoesNotThrow(() -> EarthGenConfig.validateOceanFloorY(20));
        assertDoesNotThrow(() -> EarthGenConfig.validateSeaLevel(63, 128, 20));
    }

    @Test
    void dynamicMaxTerrainYAllowsHigherMountainCeiling() {
        EarthGenConfig.setActiveMaxTerrainY(511);
        EarthGenConfig.setActiveTerrainProfile(511, EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y);

        assertEquals(511, EarthGenConfig.activeMaxTerrainY());
        assertEquals(511, EarthGenConfig.activeMaxMountainY());
        assertEquals(511, EarthGenConfig.mapMetersToTerrainY(EarthGenConfig.MAX_ABOVE_SEA_METERS));
    }

    @Test
    void configuredMountainCeilingIsCappedByActiveTerrainY() {
        EarthGenConfig.setActiveMaxTerrainY(255);
        EarthGenConfig.setActiveTerrainProfile(400, EarthGenConfig.DEFAULT_OCEAN_FLOOR_Y);
        assertEquals(255, EarthGenConfig.activeMaxMountainY());

        EarthGenConfig.setActiveMaxTerrainY(511);
        assertEquals(400, EarthGenConfig.activeMaxMountainY());
    }

    @Test
    void maxTerrainYFromNoiseSettingsUsesInclusiveTopBlock() {
        assertEquals(511, EarthGenConfig.maxTerrainYFromNoiseSettings(-64, 576));
    }
}
