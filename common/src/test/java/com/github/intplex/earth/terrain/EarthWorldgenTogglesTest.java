package com.github.intplex.earth.terrain;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthWorldgenTogglesTest {
    @Test
    void defaultsDisableRequestedFeatures() {
        EarthWorldgenToggles toggles = EarthWorldgenToggles.defaults();
        assertFalse(toggles.caves());
        assertFalse(toggles.canyons());
        assertFalse(toggles.extraUnderground());
        assertFalse(toggles.aquifers());
        assertFalse(toggles.lavaAquifers());
        assertFalse(toggles.allowsStructure(ResourceLocation.parse("minecraft:village_plains")));
        assertTrue(toggles.allowsStructure(ResourceLocation.parse("minecraft:stronghold")));
    }

    @Test
    void villagesToggleControlsAllVillageVariants() {
        EarthWorldgenToggles toggles = new EarthWorldgenToggles(
            false,
            false,
            false,
            false,
            false,
            false
        );
        assertFalse(toggles.allowsStructure(ResourceLocation.parse("minecraft:village_plains")));
        assertFalse(toggles.allowsStructure(ResourceLocation.parse("minecraft:village_taiga")));
    }

    @Test
    void nonVillageStructuresRemainEnabled() {
        EarthWorldgenToggles toggles = new EarthWorldgenToggles(
            false,
            false,
            false,
            false,
            false,
            false
        );
        assertTrue(toggles.allowsStructure(ResourceLocation.parse("minecraft:stronghold")));
    }
}
