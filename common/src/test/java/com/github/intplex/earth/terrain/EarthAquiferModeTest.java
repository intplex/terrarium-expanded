package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthAquiferModeTest {
    @Test
    void modesMapToTheExistingSerializedFlags() {
        assertFalse(EarthAquiferMode.OFF.aquifersEnabled());
        assertFalse(EarthAquiferMode.OFF.lavaAquifersEnabled());
        assertTrue(EarthAquiferMode.WATER_ONLY.aquifersEnabled());
        assertFalse(EarthAquiferMode.WATER_ONLY.lavaAquifersEnabled());
        assertTrue(EarthAquiferMode.WATER_AND_LAVA.aquifersEnabled());
        assertTrue(EarthAquiferMode.WATER_AND_LAVA.lavaAquifersEnabled());
    }

    @Test
    void existingValidFlagCombinationsRestoreTheirEditorMode() {
        assertEquals(EarthAquiferMode.OFF, EarthAquiferMode.from(toggles(false, false)));
        assertEquals(EarthAquiferMode.WATER_ONLY, EarthAquiferMode.from(toggles(true, false)));
        assertEquals(EarthAquiferMode.WATER_AND_LAVA, EarthAquiferMode.from(toggles(true, true)));
    }

    @Test
    void legacyLavaWithoutAquifersNormalizesToOff() {
        assertEquals(EarthAquiferMode.OFF, EarthAquiferMode.from(toggles(false, true)));
    }

    private static EarthWorldgenToggles toggles(boolean aquifers, boolean lavaAquifers) {
        return new EarthWorldgenToggles(false, false, false, aquifers, lavaAquifers, false);
    }
}
