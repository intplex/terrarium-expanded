package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceWaterClassifierTest {
    @Test
    void classifierDecodesCanonicalSeasonalityWaterColors() {
        assertEquals(12, SurfaceWaterClassifier.seasonalityMonths(0xFF0000AA));
        assertEquals(1, SurfaceWaterClassifier.seasonalityMonths(0xFF99D9EA));
    }

    @Test
    void classifierRejectsDryAndNoDataColorsEvenWhenOpaque() {
        assertEquals(0, SurfaceWaterClassifier.seasonalityMonths(0xFFFFFFFF));
        assertEquals(0, SurfaceWaterClassifier.seasonalityMonths(0xFFCCCCCC));
    }

    @Test
    void classifierDoesNotTreatAlphaAloneAsWater() {
        assertEquals(0, SurfaceWaterClassifier.seasonalityMonths(0x000000AA));
        assertEquals(0, SurfaceWaterClassifier.seasonalityMonths(0xFFFFFFFF));
    }

    @Test
    void classifierApproximatesRealWmtsSeasonalityShade() {
        assertEquals(11, SurfaceWaterClassifier.seasonalityMonths(0xFF0C12AF));
        assertTrue(SurfaceWaterClassifier.isWater(0xFF0C12AF, 10));
        assertFalse(SurfaceWaterClassifier.isWater(0xFF0C12AF, 12));
    }
}
