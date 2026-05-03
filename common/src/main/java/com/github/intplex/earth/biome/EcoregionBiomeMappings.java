package com.github.intplex.earth.biome;

import dev.architectury.platform.Platform;
import dev.architectury.platform.Mod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EcoregionBiomeMappings {
    static final String RESOURCE_PATH = "/data/terrarium_expanded/ecoregions/color_biome_map.csv";
    static final String CSV_HEADER =
        "UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,BIOMES_O_PLENTY_BIOME_PRIORITY,REGIONS_UNEXPLORED_BIOME,REGIONS_UNEXPLORED_BIOME_PRIORITY,NATURES_SPIRIT_BIOME,NATURES_SPIRIT_BIOME_PRIORITY,MINECRAFT_BIOME";
    static final String BIOMES_O_PLENTY_MOD_ID = "biomesoplenty";
    static final String REGIONS_UNEXPLORED_MOD_ID = "regions_unexplored";
    static final String NATURES_SPIRIT_MOD_ID = "natures_spirit";
    private static final List<BiomeProvider> AUTO_PROVIDER_TIE_BREAK_ORDER =
        List.of(BiomeProvider.REGIONS_UNEXPLORED, BiomeProvider.BIOMES_O_PLENTY, BiomeProvider.NATURES_SPIRIT);
    private static final Logger LOGGER = LoggerFactory.getLogger("terrarium_expanded.worldgen");
    private static volatile Map<Integer, BiomeSelectionIds> cachedColorToBiomeIds;
    private static volatile boolean loggedProviderState;

    private EcoregionBiomeMappings() {
    }

    public static void validateStartupBiomeMapping() {
        requireColorToBiomeIds();
    }

    public static ResolvedBiomeMapping resolveForBiomeLookup(HolderGetter<Biome> biomeLookup, BiomeIntegrationMode integrationMode) {
        Set<BiomeProvider> loadedProviders = loadedProviders();
        logProviderStateOnce(loadedProviders);
        return resolveMappings(requireColorToBiomeIds(), integrationMode, loadedProviders, biomeLookup);
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
                if (cells.size() != 11) {
                    throw new IllegalStateException("Malformed CSV row at line " + lineNumber + ": " + line);
                }

                String colorHex = cells.get(0).trim();
                String bopBiomeId = cells.get(4).trim();
                String bopPriority = cells.get(5).trim();
                String regionsUnexploredBiomeId = cells.get(6).trim();
                String regionsUnexploredPriority = cells.get(7).trim();
                String naturesSpiritBiomeId = cells.get(8).trim();
                String naturesSpiritPriority = cells.get(9).trim();
                String minecraftBiomeId = cells.get(10).trim();
                int color = parseColor(colorHex, lineNumber);
                if (minecraftBiomeId.isEmpty()) {
                    throw new IllegalStateException("Blank MINECRAFT_BIOME at line " + lineNumber);
                }

                Identifier minecraftBiomeLocation = Identifier.tryParse(minecraftBiomeId);
                if (minecraftBiomeLocation == null) {
                    throw new IllegalStateException("Invalid MINECRAFT_BIOME '" + minecraftBiomeId + "' at line " + lineNumber);
                }

                Map<BiomeProvider, ProviderBiome> providerBiomes = new HashMap<>();
                putProviderBiomeIfPresent(
                    providerBiomes,
                    BiomeProvider.BIOMES_O_PLENTY,
                    bopBiomeId,
                    bopPriority,
                    lineNumber
                );
                putProviderBiomeIfPresent(
                    providerBiomes,
                    BiomeProvider.REGIONS_UNEXPLORED,
                    regionsUnexploredBiomeId,
                    regionsUnexploredPriority,
                    lineNumber
                );
                putProviderBiomeIfPresent(
                    providerBiomes,
                    BiomeProvider.NATURES_SPIRIT,
                    naturesSpiritBiomeId,
                    naturesSpiritPriority,
                    lineNumber
                );
                validateUniqueProviderPriorities(providerBiomes, lineNumber);

                BiomeSelectionIds mapping = new BiomeSelectionIds(minecraftBiomeLocation, Map.copyOf(providerBiomes));

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
        return resolveMappings(
            byColor,
            integrationMode,
            Set.of(BiomeProvider.BIOMES_O_PLENTY, BiomeProvider.REGIONS_UNEXPLORED, BiomeProvider.NATURES_SPIRIT),
            biomeLookup
        );
    }

    static ResolvedBiomeMapping resolveMappings(
        Map<Integer, BiomeSelectionIds> byColor,
        BiomeIntegrationMode integrationMode,
        Set<BiomeProvider> loadedProviders,
        HolderGetter<Biome> biomeLookup
    ) {
        return resolveMappings(byColor, integrationMode, loadedProviders, biomeId -> requireBiomeHolder(biomeLookup, biomeId));
    }

    static ResolvedBiomeMapping resolveMappings(
        Map<Integer, BiomeSelectionIds> byColor,
        BiomeIntegrationMode integrationMode,
        Function<Identifier, Holder<Biome>> biomeResolver
    ) {
        return resolveMappings(
            byColor,
            integrationMode,
            Set.of(BiomeProvider.BIOMES_O_PLENTY, BiomeProvider.REGIONS_UNEXPLORED, BiomeProvider.NATURES_SPIRIT),
            biomeResolver
        );
    }

    static ResolvedBiomeMapping resolveMappings(
        Map<Integer, BiomeSelectionIds> byColor,
        BiomeIntegrationMode integrationMode,
        Set<BiomeProvider> loadedProviders,
        Function<Identifier, Holder<Biome>> biomeResolver
    ) {
        Map<Integer, Holder<Biome>> resolved = new HashMap<>();
        Set<Holder<Biome>> possibleBiomes = new LinkedHashSet<>();
        for (Map.Entry<Integer, BiomeSelectionIds> entry : byColor.entrySet()) {
            Holder<Biome> holder = resolvePreferredOrFallback(
                entry.getValue(),
                integrationMode == null ? BiomeIntegrationMode.AUTO : integrationMode,
                loadedProviders == null ? Set.of() : loadedProviders,
                biomeResolver
            );
            resolved.put(entry.getKey(), holder);
            possibleBiomes.add(holder);
        }

        Holder<Biome> ocean = requireBiomeHolder(biomeResolver, Biomes.OCEAN.identifier());
        Holder<Biome> coldOcean = requireBiomeHolder(biomeResolver, Biomes.COLD_OCEAN.identifier());
        Holder<Biome> lukewarmOcean = requireBiomeHolder(biomeResolver, Biomes.LUKEWARM_OCEAN.identifier());
        Holder<Biome> warmOcean = requireBiomeHolder(biomeResolver, Biomes.WARM_OCEAN.identifier());
        Holder<Biome> frozenOcean = requireBiomeHolder(biomeResolver, Biomes.FROZEN_OCEAN.identifier());
        Holder<Biome> deepOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_OCEAN.identifier());
        Holder<Biome> deepColdOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_COLD_OCEAN.identifier());
        Holder<Biome> deepLukewarmOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_LUKEWARM_OCEAN.identifier());
        Holder<Biome> deepFrozenOcean = requireBiomeHolder(biomeResolver, Biomes.DEEP_FROZEN_OCEAN.identifier());
        Holder<Biome> river = requireBiomeHolder(biomeResolver, Biomes.RIVER.identifier());
        Holder<Biome> frozenRiver = requireBiomeHolder(biomeResolver, Biomes.FROZEN_RIVER.identifier());

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

    static BiomeIntegrationMode effectiveIntegrationMode(
        BiomeIntegrationMode requestedMode,
        boolean biomesOPlentyLoaded,
        boolean regionsUnexploredLoaded,
        boolean naturesSpiritLoaded
    ) {
        if (requestedMode == BiomeIntegrationMode.AUTO
            && !biomesOPlentyLoaded
            && !regionsUnexploredLoaded
            && !naturesSpiritLoaded) {
            return BiomeIntegrationMode.VANILLA;
        }
        return requestedMode;
    }

    static BiomeIntegrationMode effectiveIntegrationMode(
        BiomeIntegrationMode requestedMode,
        boolean biomesOPlentyLoaded,
        boolean regionsUnexploredLoaded
    ) {
        return effectiveIntegrationMode(requestedMode, biomesOPlentyLoaded, regionsUnexploredLoaded, false);
    }

    static BiomeIntegrationMode effectiveIntegrationMode(BiomeIntegrationMode requestedMode, boolean biomesOPlentyLoaded) {
        return effectiveIntegrationMode(requestedMode, biomesOPlentyLoaded, false, false);
    }

    private static Set<BiomeProvider> loadedProviders() {
        Set<BiomeProvider> loaded = new LinkedHashSet<>();
        for (BiomeProvider provider : BiomeProvider.values()) {
            if (isModLoadedSafely(provider.modId())) {
                loaded.add(provider);
            }
        }
        return Set.copyOf(loaded);
    }

    private static boolean isModLoadedSafely(String modId) {
        try {
            return Platform.isModLoaded(modId);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("[TX-BIOME] could not determine loaded-mod state for {}: {}", modId, exception.toString());
            return false;
        }
    }

    private static Optional<String> modVersion(String modId) {
        try {
            return Platform.getOptionalMod(modId).map(Mod::getVersion);
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.debug("[TX-BIOME] could not determine version for {}: {}", modId, exception.toString());
            return Optional.empty();
        }
    }

    private static void logProviderStateOnce(Set<BiomeProvider> loadedProviders) {
        if (loggedProviderState) {
            return;
        }
        synchronized (EcoregionBiomeMappings.class) {
            if (loggedProviderState) {
                return;
            }
            LOGGER.info(
                "[TX-BIOME] Biomes O' Plenty loaded={} version={}; Regions Unexplored loaded={} version={}; Nature's Spirit loaded={} version={}",
                loadedProviders.contains(BiomeProvider.BIOMES_O_PLENTY),
                modVersion(BIOMES_O_PLENTY_MOD_ID).orElse("<unknown>"),
                loadedProviders.contains(BiomeProvider.REGIONS_UNEXPLORED),
                modVersion(REGIONS_UNEXPLORED_MOD_ID).orElse("<unknown>"),
                loadedProviders.contains(BiomeProvider.NATURES_SPIRIT),
                modVersion(NATURES_SPIRIT_MOD_ID).orElse("<unknown>")
            );
            loggedProviderState = true;
        }
    }

    private static Holder<Biome> resolvePreferredOrFallback(
        BiomeSelectionIds selection,
        BiomeIntegrationMode integrationMode,
        Set<BiomeProvider> loadedProviders,
        Function<Identifier, Holder<Biome>> biomeResolver
    ) {
        if (integrationMode == BiomeIntegrationMode.VANILLA) {
            return requireFallbackBiomeHolder(biomeResolver, selection);
        }
        Identifier preferredBiomeId = switch (integrationMode) {
            case AUTO -> selection.autoBiomeId(loadedProviders);
            case BIOMES_O_PLENTY -> selection.providerBiomeIdOrFallback(BiomeProvider.BIOMES_O_PLENTY);
            case REGIONS_UNEXPLORED -> selection.providerBiomeIdOrFallback(BiomeProvider.REGIONS_UNEXPLORED);
            case NATURES_SPIRIT -> selection.providerBiomeIdOrFallback(BiomeProvider.NATURES_SPIRIT);
            case VANILLA -> selection.fallbackBiomeId();
        };
        if (preferredBiomeId.equals(selection.fallbackBiomeId())) {
            return requireFallbackBiomeHolder(biomeResolver, selection);
        }
        return requirePreferredBiomeHolder(biomeResolver, preferredBiomeId, selection.fallbackBiomeId(), integrationMode);
    }

    private static Holder<Biome> requireFallbackBiomeHolder(
        Function<Identifier, Holder<Biome>> biomeResolver,
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
        Function<Identifier, Holder<Biome>> biomeResolver,
        Identifier preferredBiomeId,
        Identifier fallbackBiomeId,
        BiomeIntegrationMode integrationMode
    ) {
        try {
            return requireBiomeHolder(biomeResolver, preferredBiomeId);
        } catch (RuntimeException preferredException) {
            throw new IllegalStateException(
                "Unknown preferred biome id in ecoregion mapping (mode="
                    + integrationMode.serializedName()
                    + "): "
                    + preferredBiomeId
                    + " fallback="
                    + fallbackBiomeId,
                preferredException
            );
        }
    }

    private static Holder<Biome> requireBiomeHolder(Function<Identifier, Holder<Biome>> biomeResolver, Identifier biomeId) {
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

    private static Holder<Biome> requireBiomeHolder(HolderGetter<Biome> biomeLookup, Identifier biomeId) {
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

    private static void putProviderBiomeIfPresent(
        Map<BiomeProvider, ProviderBiome> providerBiomes,
        BiomeProvider provider,
        String biomeId,
        String priority,
        int lineNumber
    ) {
        if (biomeId.isEmpty()) {
            if (!priority.isEmpty()) {
                throw new IllegalStateException("Blank " + provider.columnName() + " with nonblank priority at line " + lineNumber);
            }
            return;
        }
        if (priority.isEmpty()) {
            throw new IllegalStateException("Blank " + provider.priorityColumnName() + " at line " + lineNumber);
        }
        Identifier biomeLocation = Identifier.tryParse(biomeId);
        if (biomeLocation == null) {
            throw new IllegalStateException("Invalid " + provider.columnName() + " '" + biomeId + "' at line " + lineNumber);
        }
        if (!provider.modId().equals(biomeLocation.getNamespace())) {
            throw new IllegalStateException(
                "Invalid "
                    + provider.columnName()
                    + " namespace '"
                    + biomeId
                    + "' at line "
                    + lineNumber
                    + "; expected "
                    + provider.modId()
                    + ":..."
            );
        }
        providerBiomes.put(provider, new ProviderBiome(biomeLocation, parsePriority(priority, provider.priorityColumnName(), lineNumber)));
    }

    private static int parsePriority(String raw, String columnName, int lineNumber) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                "Invalid " + columnName + " '" + raw + "' at line " + lineNumber + "; expected non-negative integer",
                exception
            );
        }
    }

    private static void validateUniqueProviderPriorities(Map<BiomeProvider, ProviderBiome> providerBiomes, int lineNumber) {
        Map<Integer, BiomeProvider> byPriority = new HashMap<>();
        for (Map.Entry<BiomeProvider, ProviderBiome> entry : providerBiomes.entrySet()) {
            BiomeProvider existing = byPriority.putIfAbsent(entry.getValue().priority(), entry.getKey());
            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate provider biome priority "
                        + entry.getValue().priority()
                        + " at line "
                        + lineNumber
                        + ": "
                        + existing.priorityColumnName()
                        + " and "
                        + entry.getKey().priorityColumnName()
                );
            }
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

    public enum BiomeProvider {
        BIOMES_O_PLENTY(BIOMES_O_PLENTY_MOD_ID, "BIOMES_O_PLENTY_BIOME", "BIOMES_O_PLENTY_BIOME_PRIORITY"),
        REGIONS_UNEXPLORED(REGIONS_UNEXPLORED_MOD_ID, "REGIONS_UNEXPLORED_BIOME", "REGIONS_UNEXPLORED_BIOME_PRIORITY"),
        NATURES_SPIRIT(NATURES_SPIRIT_MOD_ID, "NATURES_SPIRIT_BIOME", "NATURES_SPIRIT_BIOME_PRIORITY");

        private final String modId;
        private final String columnName;
        private final String priorityColumnName;

        BiomeProvider(String modId, String columnName, String priorityColumnName) {
            this.modId = modId;
            this.columnName = columnName;
            this.priorityColumnName = priorityColumnName;
        }

        String modId() {
            return modId;
        }

        String columnName() {
            return columnName;
        }

        String priorityColumnName() {
            return priorityColumnName;
        }
    }

    public record ProviderBiome(Identifier biomeId, int priority) {
    }

    public record BiomeSelectionIds(Identifier fallbackBiomeId, Map<BiomeProvider, ProviderBiome> providerBiomes) {
        public BiomeSelectionIds {
            providerBiomes = providerBiomes == null ? Map.of() : Map.copyOf(providerBiomes);
        }

        Identifier providerBiomeIdOrFallback(BiomeProvider provider) {
            ProviderBiome providerBiome = providerBiomes.get(provider);
            return providerBiome == null ? fallbackBiomeId : providerBiome.biomeId();
        }

        Identifier autoBiomeId(Set<BiomeProvider> loadedProviders) {
            ProviderBiome bestProviderBiome = null;
            int bestTieBreakIndex = Integer.MAX_VALUE;
            for (BiomeProvider provider : AUTO_PROVIDER_TIE_BREAK_ORDER) {
                if (!loadedProviders.contains(provider)) {
                    continue;
                }
                ProviderBiome providerBiome = providerBiomes.get(provider);
                if (providerBiome == null) {
                    continue;
                }
                int tieBreakIndex = AUTO_PROVIDER_TIE_BREAK_ORDER.indexOf(provider);
                if (bestProviderBiome == null
                    || providerBiome.priority() < bestProviderBiome.priority()
                    || (providerBiome.priority() == bestProviderBiome.priority() && tieBreakIndex < bestTieBreakIndex)) {
                    bestProviderBiome = providerBiome;
                    bestTieBreakIndex = tieBreakIndex;
                }
            }
            return bestProviderBiome == null ? fallbackBiomeId : bestProviderBiome.biomeId();
        }
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
