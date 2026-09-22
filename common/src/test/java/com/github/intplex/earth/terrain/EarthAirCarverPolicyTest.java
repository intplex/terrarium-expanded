package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthAirCarverPolicyTest {
    @Test
    void unclassifiedAndModdedAirCarversBelongToCaves() {
        assertEquals(
            EarthAirCarverPolicy.AirCarverFamily.CAVE,
            EarthAirCarverPolicy.classify(null, false)
        );
        assertEquals(
            EarthAirCarverPolicy.AirCarverFamily.CAVE,
            EarthAirCarverPolicy.classify("giant_caverns", false)
        );
    }

    @Test
    void recognizesCanyonAndExtraUndergroundFamilies() {
        assertEquals(
            EarthAirCarverPolicy.AirCarverFamily.CANYON,
            EarthAirCarverPolicy.classify("winding_ravine", false)
        );
        assertEquals(
            EarthAirCarverPolicy.AirCarverFamily.CANYON,
            EarthAirCarverPolicy.classify("custom_shape", true)
        );
        assertEquals(
            EarthAirCarverPolicy.AirCarverFamily.EXTRA_UNDERGROUND,
            EarthAirCarverPolicy.classify("cave_extra_underground", false)
        );
    }

    @Test
    void disabledCavesRejectUnclassifiedAirCarvers() {
        EarthWorldgenToggles disabled = EarthWorldgenToggles.defaults();
        assertFalse(EarthAirCarverPolicy.allows(EarthAirCarverPolicy.AirCarverFamily.CAVE, disabled));

        EarthWorldgenToggles cavesEnabled = new EarthWorldgenToggles(
            true,
            false,
            false,
            false,
            false,
            false
        );
        assertTrue(EarthAirCarverPolicy.allows(EarthAirCarverPolicy.AirCarverFamily.CAVE, cavesEnabled));
    }
}
