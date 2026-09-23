package com.github.intplex.earth.terrain;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EarthGenerationProfileCodecTest {
    @TempDir Path tempDir;

    @BeforeEach
    @AfterEach
    void shutdown() {
        TerrainServices.resetForTesting();
    }

    @Test
    void legacyValuesAreCapturedAndSavedEvenAfterPropertiesChange() throws Exception {
        legacyConfig(false, 4);
        EarthGenerationProfile migrated = decode("{\"zoom\":10}");
        assertEquals(new InlandWaterSettings(false, 4), migrated.inlandWater());
        var saved = EarthGenerationProfile.CODEC.codec().encodeStart(NbtOps.INSTANCE, migrated).getOrThrow();

        legacyConfig(true, 12);
        EarthGenerationProfile restored = EarthGenerationProfile.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertEquals(migrated, restored);
        TerrainServices.syncEarthProfile(restored);
        assertEquals(migrated.inlandWater(), TerrainServices.requireContext().terrainRuntimeState().inlandWaterSettings());
        assertEquals(migrated.inlandWater(), restored.withTerrainShape(11, 280, 0).inlandWater());
    }

    @Test
    void explicitSettingsAndPartialObjectsDoNotReadLegacyProperties() throws Exception {
        legacyConfig(false, 3);
        EarthGenerationProfile explicit = decode("{\"inland_water\":{\"enabled\":true,\"min_water_months\":8}}");
        assertEquals(new InlandWaterSettings(true, 8), explicit.inlandWater());
        assertEquals(InlandWaterSettings.DEFAULT, decode("{\"inland_water\":{}}").inlandWater());
        assertEquals(new InlandWaterSettings(false, 10), decode("{\"inland_water\":{\"enabled\":false}}").inlandWater());
    }

    @Test
    void missingLegacyPropertiesResolveToDefaultsAndAreAlwaysEncoded() {
        EarthGenerationProfile defaults = decode("{}");
        assertEquals(InlandWaterSettings.DEFAULT, defaults.inlandWater());
        JsonElement saved = EarthGenerationProfile.CODEC.codec().encodeStart(JsonOps.INSTANCE, defaults).getOrThrow();
        assertTrue(saved.getAsJsonObject().has("inland_water"));
        assertEquals(defaults, EarthGenerationProfile.CODEC.codec().parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    @Test
    void explicitInvalidValuesFailInsteadOfUsingLegacyFallback() {
        for (String invalid : new String[] {
            "{\"min_water_months\":0}", "{\"min_water_months\":13}",
            "{\"min_water_months\":\"ten\"}", "{\"enabled\":\"yes\"}", "false"
        }) {
            assertTrue(EarthGenerationProfile.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"inland_water\":" + invalid + "}")).error().isPresent(), invalid);
        }
    }

    @Test
    void legacyOutOfRangeValuesKeepTheirOldClampingBehavior() throws Exception {
        legacyConfig(false, 99);
        assertEquals(new InlandWaterSettings(false, 12), decode("{}").inlandWater());
    }

    private static EarthGenerationProfile decode(String json) {
        return EarthGenerationProfile.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private void legacyConfig(boolean enabled, int months) throws Exception {
        TerrainServices.shutdown();
        Path configDir = Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(configDir.resolve(TerrariumRuntimeConfig.FILE_NAME),
            "inland_water.enabled=" + enabled + "\ninland_water.min_water_months=" + months + "\n");
        TerrainServices.bootstrap(tempDir);
    }
}
