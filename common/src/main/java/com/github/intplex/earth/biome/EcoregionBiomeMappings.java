package com.github.intplex.earth.biome;

import dev.architectury.platform.Platform;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcoregionBiomeMappings {
    static final String RESOURCE_PATH = "/data/terrarium_expanded/ecoregions/color_biome_map.csv";
    static final String CSV_HEADER = "UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME";
    static final String BIOMES_O_PLENTY_MOD_ID = "biomesoplenty";
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static volatile Map<Integer, BiomeSelectionIds> cachedColorToBiomeIds;

    private EcoregionBiomeMappings() {
    }

    public static void validateStartupBiomeMapping() {
        requireColorToBiomeIds();
    }

    public static ResolvedBiomeMapping resolveForBiomeLookup(HolderGetter<Biome> biomeLookup, BiomeIntegrationMode integrationMode) {
        BiomeIntegrationMode effectiveMode =
            effectiveIntegrationMode(integrationMode, isModLoadedSafely(BIOMES_O_PLENTY_MOD_ID));
        return resolveMappings(requireColorToBiomeIds(), effectiveMode, biomeLookup);
    }

    public static ResolvedBiomeMapping resolveForBiomeLookup(HolderGetter<Biome> biomeLookup) {
        return resolveForBiomeLookup(biomeLookup, BiomeIntegrationMode.AUTO);
    }

    public static Map<Integer, BiomeSelectionIds> requireColorToBiomeIds() {
        Map<Integer, BiomeSelectionIds> local = cachedColorToBiomeIds;
        if (local != null) {
            return local;
        }
        synchronized (EcoregionBiomeMappings.class) {
            local = cachedColorToBiomeIds;
            if (local == null) {
                local = loadColorToBiomeIds();
                cachedColorToBiomeIds = local;
            }
            return local;
        }
    }

    static Map<Integer, BiomeSelectionIds> parseMappingsCsv(Reader reader) {
        try (BufferedReader buffered = new BufferedReader(reader)) {
            String header = buffered.readLine();
            if (header == null) {
                throw new IllegalStateException("Ecoregion biome mapping CSV is empty");
            }
            if (!CSV_HEADER.equals(header.trim())) {
                throw new IllegalStateException(
                    "Unexpected ecoregion biome mapping CSV header; expected "
                        + CSV_HEADER
                        + " but got: "
                        + header
                );
            }

            Map<Integer, BiomeSelectionIds> mappings = new HashMap<>();
            String line;
            int lineNumber = 1;
            while ((line = buffered.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = parseCsvRow(line);
                if (cells.size() != 6) {
                    throw new IllegalStateException("Malformed CSV row at line " + lineNumber + ": " + line);
                }

                String colorHex = cells.get(0).trim();
                String bopBiomeId = cells.get(4).trim();
                String minecraftBiomeId = cells.get(5).trim();
                int color = parseColor(colorHex, lineNumber);
                if (minecraftBiomeId.isEmpty()) {
                    throw new IllegalStateException("Blank MINECRAFT_BIOME at line " + lineNumber);
                }

                ResourceLocation minecraftBiomeLocation = ResourceLocation.tryParse(minecraftBiomeId);
                if (minecraftBiomeLocation == null) {
                    throw new IllegalStateException("Invalid MINECRAFT_BIOME '" + minecraftBiomeId + "' at line " + lineNumber);
                }

                ResourceLocation preferredBiomeLocation = minecraftBiomeLocation;
                if (!bopBiomeId.isEmpty()) {
                    ResourceLocation bopBiomeLocation = ResourceLocation.tryParse(bopBiomeId);
                    if (bopBiomeLocation == null) {
                        throw new IllegalStateException(
                            "Invalid BIOMES_O_PLENTY_BIOME '" + bopBiomeId + "' at line " + lineNumber
                        );
                    }
                    if (!BIOMES_O_PLENTY_MOD_ID.equals(bopBiomeLocation.getNamespace())) {
                        throw new IllegalStateException(
                            "Invalid BIOMES_O_PLENTY_BIOME namespace '" + bopBiomeId + "' at line "
                                + lineNumber
                                + "; expected "
                                + BIOMES_O_PLENTY_MOD_ID
                                + ":..."
                        );
                    }
                    preferredBiomeLocation = bopBiomeLocation;
                }

                BiomeSelectionIds mapping = new BiomeSelectionIds(preferredBiomeLocation, minecraftBiomeLocation);

                BiomeSelectionIds previous = mappings.putIfAbsent(color, mapping);
                if (previous != null && !previous.equals(mapping)) {
                    throw new IllegalStateException(
                        "Conflicting biome ids for color " + colorHex + ": " + previous + " vs " + mapping
                    );
                }
            }

            if (mappings.isEmpty()) {
                throw new IllegalStateException("Ecoregion biome mapping CSV did not contain any rows");
            }
            return Map.copyOf(mappings);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed reading ecoregion biome mapping CSV", exception);
        }
    }

    private static Map<Integer, BiomeSelectionIds> loadColorToBiomeIds() {
        InputStream stream = EcoregionBiomeMappings.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Missing ecoregion biome mapping resource: " + RESOURCE_PATH);
        }

        Map<Integer, BiomeSelectionIds> byColor;
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            byColor = parseMappingsCsv(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed loading ecoregion biome mapping resource: " + RESOURCE_PATH, exception);
        }
        return byColor;
    }

    static ResolvedBiomeMapping resolveMappings(
        Map<Integer, BiomeSelectionIds> byColor,
        BiomeIntegrationMode integrationMode,
        HolderGetter<Biome> biomeLookup
    ) {
        return resolveMappings(byColor, integrationMode, biomeId -> requireBiomeHolder(biomeLookup, biomeId));
    }

    static ResolvedBiomeMapping resolveMappings(
        Map<Integer, BiomeSelectionIds> byColor,
        BiomeIntegrationMode integrationMode,
        Function<ResourceLocation, Holder<Biome>> biomeResolver
    ) {
        Map<Integer, Holder<Biome>> resolved = new HashMap<>();
        Set<Holder<Biome>> possibleBiomes = new LinkedHashSet<>();
        for (Map.Entry<Integer, BiomeSelectionIds> entry : byColor.entrySet()) {
            Holder<Biome> holder = resolvePreferredOrFallback(entry.getValue(), integrationMode, biomeResolver);
            resolved.put(entry.getKey(), holder);
            possibleBiomes.add(holder);
        }

        Holder<Biome> ocean = requireBiomeHolder(biomeResolver, Biomes.OCEAN.location());
        Holder<Biome> coldOcean = requireBiomeHolder(biomeResolver, Biomes.COLD_OCEAN.location());
        Holder<Biome> lukewarmOcean = requireBiomeHolder(biomeResolver, Biomes.LUKEWARM_OCEAN.location());
        Holder<Biome> warmOcean = requireBiomeHolder(biomeResolver, Biomes.WARM_OCEAN.location());
        Holder<Biome> frozenOcean = requireBiomeHolder(biomeResolver, Biomes.FROZEN_OCEAN.location());
        Holder<Biome> deepOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_OCEAN.location());
        Holder<Biome> deepColdOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_COLD_OCEAN.location());
        Holder<Biome> deepLukewarmOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_LUKEWARM_OCEAN.location());
        Holder<Biome> deepFrozenOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_FROZEN_OCEAN.location());
        Holder<Biome> river = requireBiomeHolder(biomeResolver, Biomes.RIVER.location());
        Holder<Biome> frozenRiver = requireBiomeHolder(biomeResolver, Biomes.FROZEN_RIVER.location());

        possibleBiomes.add(ocean);
        possibleBiomes.add(coldOcean);
        possibleBiomes.add(lukewarmOcean);
        possibleBiomes.add(warmOcean);
        possibleBiomes.add(frozenOcean);
        possibleBiomes.add(deepOcean);
        possibleBiomes.add(deepColdOcean);
        possibleBiomes.add(deepLukewarmOcean);
        possibleBiomes.add(deepFrozenOcean);
        possibleBiomes.add(river);
        possibleBiomes.add(frozenRiver);

        return new ResolvedBiomeMapping(
            Map.copyOf(resolved),
            ocean,
            coldOcean,
            lukewarmOcean,
            warmOcean,
            frozenOcean,
            deepOcean,
            deepColdOcean,
            deepLukewarmOcean,
            deepFrozenOcean,
            river,
            frozenRiver,
            Set.copyOf(possibleBiomes)
        );
    }

    static BiomeIntegrationMode effectiveIntegrationMode(BiomeIntegrationMode requestedMode, boolean biomesOPlentyLoaded) {
        if (requestedMode == BiomeIntegrationMode.AUTO && !biomesOPlentyLoaded) {
            return BiomeIntegrationMode.VANILLA;
        }
        return requestedMode;
    }

    private static boolean isModLoadedSafely(String modId) {
        try {
            return Platform.isModLoaded(modId);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("[TX-BIOME] could not determine loaded-mod state for {}: {}", modId, exception.toString());
            return false;
        }
    }

    private static Holder<Biome> resolvePreferredOrFallback(
        BiomeSelectionIds selection,
        BiomeIntegrationMode integrationMode,
        Function<ResourceLocation, Holder<Biome>> biomeResolver
    ) {
        if (integrationMode == BiomeIntegrationMode.VANILLA) {
            return requireFallbackBiomeHolder(biomeResolver, selection);
        }
        return requirePreferredBiomeHolder(biomeResolver, selection, integrationMode);
    }

    private static Holder<Biome> requireFallbackBiomeHolder(
        Function<ResourceLocation, Holder<Biome>> biomeResolver,
        BiomeSelectionIds selection
    ) {
        try {
            return requireBiomeHolder(biomeResolver, selection.fallbackBiomeId());
        } catch (RuntimeException fallbackException) {
            throw new IllegalStateException(
                "Unknown fallback biome id in ecoregion mapping: " + selection.fallbackBiomeId(),
                fallbackException
            );
        }
    }

    private static Holder<Biome> requirePreferredBiomeHolder(
        Function<ResourceLocation, Holder<Biome>> biomeResolver,
        BiomeSelectionIds selection,
        BiomeIntegrationMode integrationMode
    ) {
        try {
            return requireBiomeHolder(biomeResolver, selection.preferredBiomeId());
        } catch (RuntimeException preferredException) {
            throw new IllegalStateException(
                "Unknown preferred biome id in ecoregion mapping (mode="
                    + integrationMode.serializedName()
                    + "): "
                    + selection.preferredBiomeId()
                    + " fallback="
                    + selection.fallbackBiomeId(),
                preferredException
            );
        }
    }

    private static Holder<Biome> requireBiomeHolder(Function<ResourceLocation, Holder<Biome>> biomeResolver, ResourceLocation biomeId) {
        try {
            Holder<Biome> holder = biomeResolver.apply(biomeId);
            if (holder == null) {
                throw new IllegalStateException("resolver returned null");
            }
            if (!holder.isBound()) {
                throw new IllegalStateException("resolver returned unbound holder");
            }
            return holder;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unknown biome id in ecoregion mapping: " + biomeId, exception);
        }
    }

    private static Holder<Biome> requireBiomeHolder(HolderGetter<Biome> biomeLookup, ResourceLocation biomeId) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, biomeId);
        Holder<Biome> holder = biomeLookup.get(key).orElseThrow(() -> new IllegalStateException("resolver returned missing holder"));
        if (!holder.isBound()) {
            throw new IllegalStateException("resolver returned unbound holder");
        }
        return holder;
    }

    private static int parseColor(String colorHex, int lineNumber) {
        if (colorHex.length() != 7 || colorHex.charAt(0) != '#') {
            throw new IllegalStateException(
                "Invalid UNIQUE_ECOREGION_COLOR '" + colorHex + "' at line " + lineNumber + "; expected #RRGGBB"
            );
        }
        try {
            return Integer.parseInt(colorHex.substring(1), 16);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid UNIQUE_ECOREGION_COLOR '" + colorHex + "' at line " + lineNumber + "; expected #RRGGBB",
                exception
            );
        }
    }

    private static List<String> parseCsvRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (current == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    cell.append('"');
                    index += 2;
                    continue;
                }
                inQuotes = !inQuotes;
                index++;
                continue;
            }
            if (current == ',' && !inQuotes) {
                cells.add(cell.toString());
                cell.setLength(0);
                index++;
                continue;
            }
            cell.append(current);
            index++;
        }
        if (inQuotes) {
            throw new IllegalStateException("Malformed CSV row with unclosed quote: " + line);
        }
        cells.add(cell.toString());
        return cells;
    }

    public record BiomeSelectionIds(ResourceLocation preferredBiomeId, ResourceLocation fallbackBiomeId) {
    }

    public record ResolvedBiomeMapping(
        Map<Integer, Holder<Biome>> byColor,
        Holder<Biome> oceanBiome,
        Holder<Biome> coldOceanBiome,
        Holder<Biome> lukewarmOceanBiome,
        Holder<Biome> warmOceanBiome,
        Holder<Biome> frozenOceanBiome,
        Holder<Biome> deepOceanBiome,
        Holder<Biome> deepColdOceanBiome,
        Holder<Biome> deepLukewarmOceanBiome,
        Holder<Biome> deepFrozenOceanBiome,
        Holder<Biome> riverBiome,
        Holder<Biome> frozenRiverBiome,
        Set<Holder<Biome>> possibleBiomes
    ) {
    }

}
