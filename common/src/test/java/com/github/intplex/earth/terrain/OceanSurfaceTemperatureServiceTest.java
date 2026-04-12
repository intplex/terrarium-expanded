package com.github.intplex.earth.terrain;

import java.util.OptionalDouble;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OceanSurfaceTemperatureServiceTest {
    @AfterEach
    void clearInjectedGrid() {
        OceanSurfaceTemperatureService.setPackedSstForTesting(null);
    }

    @Test
    void samplesBilinearWithinGrid() {
        short[] grid = new short[OceanSurfaceTemperatureService.CELL_COUNT];
        int row0 = 10;
        int row1 = 11;
        int col0 = 20;
        int col1 = 21;
        grid[index(row0, col0)] = encode(10.0);
        grid[index(row0, col1)] = encode(20.0);
        grid[index(row1, col0)] = encode(30.0);
        grid[index(row1, col1)] = encode(40.0);
        OceanSurfaceTemperatureService.setPackedSstForTesting(grid);

        double latitude = OceanSurfaceTemperatureService.LAT_START_CENTER + 10.75;
        double longitude = OceanSurfaceTemperatureService.LON_START_CENTER + 20.25;
        double sampled = OceanSurfaceTemperatureService.sampleMeanAnnualSst(latitude, longitude);

        assertEquals(27.5, sampled, 1.0e-6);
    }

    @Test
    void wrapsLongitudeAcrossDateline() {
        short[] grid = new short[OceanSurfaceTemperatureService.CELL_COUNT];
        int row = 50;
        grid[index(row, 359)] = encode(10.0);
        grid[index(row, 0)] = encode(30.0);
        OceanSurfaceTemperatureService.setPackedSstForTesting(grid);

        double latitude = OceanSurfaceTemperatureService.LAT_START_CENTER + row;
        double longitude = 179.75;
        double sampled = OceanSurfaceTemperatureService.sampleMeanAnnualSst(latitude, longitude);

        assertEquals(15.0, sampled, 1.0e-6);
    }

    @Test
    void outOfBoundsBlocksReturnEmpty() {
        short[] grid = new short[OceanSurfaceTemperatureService.CELL_COUNT];
        OceanSurfaceTemperatureService.setPackedSstForTesting(grid);

        OptionalDouble sampled = OceanSurfaceTemperatureService.sampleMeanAnnualSstAtBlock(Integer.MIN_VALUE, Integer.MIN_VALUE, 8);
        assertTrue(sampled.isEmpty());
    }

    private static int index(int lat, int lon) {
        return lat * OceanSurfaceTemperatureService.LON_CELLS + lon;
    }

    private static short encode(double celsius) {
        return (short) Math.round(celsius / OceanSurfaceTemperatureService.SCALE_C);
    }
}
