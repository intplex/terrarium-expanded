package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlandWaterAnalysisTest {
    @Test
    void rawSurfaceWaterAboveSeaBecomesOneBlockDeepInlandWater() {
        int[][] terrain = filledInts(5, 90);
        boolean[][] rawWater = new boolean[5][5];
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[5][5], rawWater, enabledSettings());

        assertTrue(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.RIVER, result.waterKind()[2][2]);
        assertEquals(90, result.waterSurfaceY()[2][2]);
        assertEquals(89, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void rawSurfaceWaterAtSeaLevelStillBecomesInlandWater() {
        int[][] terrain = filledInts(5, EarthGenConfig.SEA_LEVEL);
        boolean[][] rawWater = new boolean[5][5];
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[5][5], rawWater, enabledSettings());

        assertTrue(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.RIVER, result.waterKind()[2][2]);
        assertEquals(EarthGenConfig.SEA_LEVEL, result.waterSurfaceY()[2][2]);
        assertEquals(EarthGenConfig.SEA_LEVEL - 1, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void lowPositiveTerrainCompressedToSeaLevelMinusOneStillKeepsInlandWater() {
        int[][] terrain = filledInts(5, EarthGenConfig.SEA_LEVEL - 1);
        boolean[][] rawWater = new boolean[5][5];
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[5][5], rawWater, enabledSettings());

        assertTrue(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.RIVER, result.waterKind()[2][2]);
        assertEquals(EarthGenConfig.SEA_LEVEL - 1, result.waterSurfaceY()[2][2]);
        assertEquals(EarthGenConfig.SEA_LEVEL - 2, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void inlandWaterDepthIncreasesAwayFromShore() {
        int[][] terrain = filledInts(9, 100);
        boolean[][] rawWater = filledBools(9, true);

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[9][9], rawWater, enabledSettings());

        assertEquals(99, result.effectiveSolidTopY()[0][0]);
        assertEquals(97, result.effectiveSolidTopY()[4][4]);
        assertTrue(result.effectiveSolidTopY()[4][4] < result.effectiveSolidTopY()[0][0]);
    }

    @Test
    void inlandWaterDepthCapsAtSevenBlocks() {
        int[][] terrain = filledInts(27, 120);
        boolean[][] rawWater = filledBools(27, true);

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[27][27], rawWater, enabledSettings());

        assertEquals(113, result.effectiveSolidTopY()[13][13]);
    }

    @Test
    void dryPixelsRemainDry() {
        InlandWaterAnalysis.Result result = analyze(filledInts(5, 90), new boolean[5][5], new boolean[5][5], enabledSettings());

        assertFalse(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.NONE, result.waterKind()[2][2]);
        assertEquals(EarthGenConfig.MIN_Y, result.waterSurfaceY()[2][2]);
        assertEquals(90, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void oceanPixelsAreIgnoredEvenWhenWaterMaskIsSet() {
        int[][] terrain = filledInts(5, 90);
        boolean[][] ocean = new boolean[5][5];
        boolean[][] rawWater = new boolean[5][5];
        ocean[2][2] = true;
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, ocean, rawWater, enabledSettings());

        assertFalse(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.NONE, result.waterKind()[2][2]);
        assertEquals(90, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void oceanBackedSurfaceWaterIsIgnored() {
        int[][] terrain = filledInts(5, EarthGenConfig.SEA_LEVEL - 1);
        boolean[][] ocean = new boolean[5][5];
        boolean[][] rawWater = new boolean[5][5];
        ocean[2][2] = true;
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, ocean, rawWater, enabledSettings());

        assertFalse(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.NONE, result.waterKind()[2][2]);
        assertEquals(EarthGenConfig.MIN_Y, result.waterSurfaceY()[2][2]);
        assertEquals(EarthGenConfig.SEA_LEVEL - 1, result.effectiveSolidTopY()[2][2]);
    }

    @Test
    void disabledSettingsSkipInlandWaterPlacement() {
        int[][] terrain = filledInts(5, 90);
        boolean[][] rawWater = new boolean[5][5];
        rawWater[2][2] = true;

        InlandWaterAnalysis.Result result = analyze(terrain, new boolean[5][5], rawWater, disabledSettings());

        assertFalse(result.inlandMask()[2][2]);
        assertEquals(WaterBodyKind.NONE, result.waterKind()[2][2]);
        assertEquals(EarthGenConfig.MIN_Y, result.waterSurfaceY()[2][2]);
        assertEquals(90, result.effectiveSolidTopY()[2][2]);
    }

    private static InlandWaterAnalysis.Result analyze(int[][] terrain, boolean[][] ocean, boolean[][] rawWater, InlandWaterSettings settings) {
        int size = terrain.length;
        return InlandWaterAnalysis.analyze(filledBools(size, true), terrain, ocean, rawWater, settings, 0, 0, size, size);
    }

    private static InlandWaterSettings enabledSettings() {
        return new InlandWaterSettings(true, 10);
    }

    private static InlandWaterSettings disabledSettings() {
        return new InlandWaterSettings(false, 10);
    }

    private static int[][] filledInts(int size, int value) {
        int[][] grid = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                grid[x][z] = value;
            }
        }
        return grid;
    }

    private static boolean[][] filledBools(int size, boolean value) {
        boolean[][] grid = new boolean[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                grid[x][z] = value;
            }
        }
        return grid;
    }
}
