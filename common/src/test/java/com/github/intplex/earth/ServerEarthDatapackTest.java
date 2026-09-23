package com.github.intplex.earth;

import com.github.intplex.earth.biome.BiomeIntegrationMode;
import com.github.intplex.earth.terrain.EarthGenerationProfile;
import com.github.intplex.earth.terrain.InlandWaterSettings;
import com.github.intplex.earth.terrain.TerrainHeightMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

import static org.junit.jupiter.api.Assertions.*;

class ServerEarthDatapackTest {
    private static final String PRESET = "data/terrarium_expanded/worldgen/world_preset/earth.json";
    private static final Path PACK = Path.of(System.getProperty("terrarium.serverPackDir"));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void templateOverridesBundledPresetWithMatchingCodecDefaults() throws Exception {
        JsonObject starter = read(PACK.resolve(PRESET));
        try (var stream = getClass().getResourceAsStream("/" + PRESET)) {
            assertNotNull(stream);
            JsonObject bundled = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(bundled.get("dimensions"), starter.get("dimensions"));
        }
        JsonObject source = source(starter);
        var profile = EarthGenerationProfile.CODEC.codec().parse(JsonOps.INSTANCE, source).getOrThrow();
        assertEquals(new EarthGenerationProfile(8, 256, 0), profile);
        assertEquals(InlandWaterSettings.DEFAULT, profile.inlandWater());
        var metadata = PackMetadataSection.SERVER_TYPE.codec()
            .parse(JsonOps.INSTANCE, read(PACK.resolve("pack.mcmeta")).getAsJsonObject("pack")).getOrThrow();
        var currentFormat = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        assertEquals(currentFormat, metadata.supportedFormats().minInclusive());
        assertEquals(currentFormat, metadata.supportedFormats().maxInclusive());
    }

    @Test
    void schemaDescribesEveryCodecFieldAndTemplateDefault() throws Exception {
        JsonObject starter = read(PACK.resolve(PRESET));
        Path schemaPath = PACK.resolve(PRESET).getParent().resolve(starter.get("$schema").getAsString()).normalize();
        assertTrue(schemaPath.startsWith(PACK));
        JsonObject schema = read(schemaPath);
        JsonObject sourceSchema = schema.getAsJsonObject("properties").getAsJsonObject("dimensions")
            .getAsJsonObject("properties").getAsJsonObject("minecraft:overworld")
            .getAsJsonObject("properties").getAsJsonObject("generator")
            .getAsJsonObject("properties").getAsJsonObject("biome_source");
        Set<String> fields = EarthGenerationProfile.CODEC.keys(JsonOps.INSTANCE)
            .map(JsonElement::getAsString).collect(Collectors.toCollection(HashSet::new));
        fields.addAll(Set.of("type", "biome_integration"));
        assertEquals(fields, sourceSchema.getAsJsonObject("properties").keySet());
        checkDefaults(source(starter), sourceSchema);
        JsonObject properties = sourceSchema.getAsJsonObject("properties");
        assertEquals(EarthGenConfig.MIN_ZOOM, properties.getAsJsonObject("zoom").get("minimum").getAsInt());
        assertEquals(EarthGenConfig.MAX_ZOOM, properties.getAsJsonObject("zoom").get("maximum").getAsInt());
        assertEquals(EarthGenConfig.ABSOLUTE_MAX_TERRAIN_Y, properties.getAsJsonObject("max_mountain_y").get("maximum").getAsInt());
        Set<String> modes = Arrays.stream(TerrainHeightMode.values()).map(TerrainHeightMode::getSerializedName).collect(Collectors.toSet());
        assertEquals(modes, strings(properties.getAsJsonObject("above_sea_height_mode").get("enum")));
        assertEquals(modes, strings(properties.getAsJsonObject("below_sea_height_mode").get("enum")));
        Set<String> integrations = Arrays.stream(BiomeIntegrationMode.values()).map(BiomeIntegrationMode::serializedName).collect(Collectors.toCollection(HashSet::new));
        integrations.add("expanded");
        assertEquals(integrations, strings(properties.getAsJsonObject("biome_integration").get("enum")));
    }

    @Test
    void zipHasInstallableRootAndWorkingOfflineSchemaReference() throws Exception {
        try (ZipFile zip = new ZipFile(System.getProperty("terrarium.serverPackZip"))) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("README.md"));
            Set<String> presets = zip.stream()
                .map(entry -> entry.getName())
                .filter(name -> name.contains("/worldgen/world_preset/") && name.endsWith(".json"))
                .collect(Collectors.toSet());
            assertEquals(Set.of(PRESET), presets, "The starter must override the bundled Earth preset without adding another level type");
            var entry = zip.getEntry(PRESET);
            assertNotNull(entry);
            JsonObject preset = JsonParser.parseString(new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String schema = Path.of(PRESET).getParent().resolve(preset.get("$schema").getAsString()).normalize().toString().replace('\\', '/');
            assertNotNull(zip.getEntry(schema));
            assertEquals(read(PACK.resolve(PRESET)), preset);
        }
    }

    private static void checkDefaults(JsonObject values, JsonObject schema) {
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        JsonObject properties = schema.getAsJsonObject("properties");
        for (var entry : values.entrySet()) {
            JsonObject field = properties.getAsJsonObject(entry.getKey());
            assertNotNull(field, entry.getKey());
            assertTrue(field.has("description"), entry.getKey());
            if (entry.getValue().isJsonObject()) {
                checkDefaults(entry.getValue().getAsJsonObject(), field);
            } else {
                assertEquals(entry.getValue(), field.has("const") ? field.get("const") : field.get("default"), entry.getKey());
            }
        }
    }

    private static Set<String> strings(JsonElement array) {
        return array.getAsJsonArray().asList().stream().map(JsonElement::getAsString).collect(Collectors.toSet());
    }

    private static JsonObject source(JsonObject preset) {
        return preset.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
            .getAsJsonObject("generator").getAsJsonObject("biome_source");
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
