package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthSurfaceRuleGuardTest {
    @Test
    void allowsTheOuterTerrainSurfaceAndItsTopsoil() {
        assertFalse(EarthSurfaceRuleGuard.shouldSkipSurfaceRuleForTesting(70, 70, 60));
        assertFalse(EarthSurfaceRuleGuard.shouldSkipSurfaceRuleForTesting(65, 70, 60));
    }

    @Test
    void skipsSurfaceRulesBelowFirstUndergroundAirGap() {
        assertTrue(EarthSurfaceRuleGuard.shouldSkipSurfaceRuleForTesting(59, 70, 60));
        assertTrue(EarthSurfaceRuleGuard.shouldSkipSurfaceRuleForTesting(40, 70, 60));
    }

    @Test
    void allowsSolidColumnsWithoutInteriorAir() {
        assertFalse(EarthSurfaceRuleGuard.shouldSkipSurfaceRuleForTesting(
            40,
            70,
            EarthSurfaceRuleGuard.noInteriorAirForTesting()
        ));
    }
}
