package com.github.intplex.earth.biome;

import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionBiomeMappingsTest {
    private static final String HEADER = EcoregionBiomeMappings.CSV_HEADER;

    @Test
    void effectiveIntegrationModeUsesVanillaWhenAutoAndProvidersMissing() {
        assertEquals(
            BiomeIntegrationMode.VANILLA,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.AUTO, false, false)
        );
    }

    @Test
    void effectiveIntegrationModeKeepsAutoWhenAnyProviderPresent() {
        assertEquals(
            BiomeIntegrationMode.AUTO,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.AUTO, true, false)
        );
        assertEquals(
            BiomeIntegrationMode.AUTO,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.AUTO, false, true)
        );
    }

    @Test
    void legacyExpandedModeDeserializesAsBiomesOPlenty() {
        assertEquals(BiomeIntegrationMode.BIOMES_O_PLENTY, BiomeIntegrationMode.fromSerializedName("expanded"));
    }

    @Test
    void parseMappingsCsvParsesValidRows() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,, ,regions_unexplored:rainforest,90,minecraft:plains
            #AABBCC,Other Eco,Other Biome,Palearctic,biomesoplenty:maple_woods,100,regions_unexplored:maple_forest,90,minecraft:forest
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings =
            EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));

        assertEquals(2, mappings.size());
        assertEquals(Identifier.parse("minecraft:plains"), mappings.get(0x112233).fallbackBiomeId());
        assertEquals(
            Identifier.parse("regions_unexplored:rainforest"),
            mappings.get(0x112233)
                .providerBiomes()
                .get(EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED)
                .biomeId()
        );
        assertEquals(
            Identifier.parse("biomesoplenty:maple_woods"),
            mappings.get(0xAABBCC)
                .providerBiomes()
                .get(EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY)
                .biomeId()
        );
    }

    @Test
    void parseMappingsCsvRejectsLegacyHeader() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,MINECRAFT_BIOME
            #112233,minecraft:plains
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("expected " + HEADER));
    }

    @Test
    void parseMappingsCsvRejectsBlankBiome() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,100,regions_unexplored:maple_forest,90,
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Blank MINECRAFT_BIOME"));
    }

    @Test
    void parseMappingsCsvAcceptsBlankProviderBiomesAndPriorities() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,,,,,minecraft:plains
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings =
            EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));
        assertEquals(Identifier.parse("minecraft:plains"), mappings.get(0x112233).fallbackBiomeId());
        assertTrue(mappings.get(0x112233).providerBiomes().isEmpty());
    }

    @Test
    void parseMappingsCsvRejectsBlankPriorityForProviderBiome() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,,regions_unexplored:maple_forest,90,minecraft:forest
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Blank BIOMES_O_PLENTY_BIOME_PRIORITY"));
    }

    @Test
    void parseMappingsCsvRejectsPriorityWithoutProviderBiome() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,,100,regions_unexplored:maple_forest,90,minecraft:forest
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Blank BIOMES_O_PLENTY_BIOME with nonblank priority"));
    }

    @Test
    void parseMappingsCsvHandlesQuotedBiomeNameWithComma() {
        String csv = HEADER + """

            #112233,Test Eco,"Mediterranean Forests, Woodlands & Scrub",Nearctic,biomesoplenty:maple_woods,100,regions_unexplored:maple_forest,90,minecraft:forest
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings =
            EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));
        assertEquals(Identifier.parse("minecraft:forest"), mappings.get(0x112233).fallbackBiomeId());
    }

    @Test
    void parseMappingsCsvRejectsInvalidProviderBiome() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,not a biome,100,regions_unexplored:maple_forest,90,minecraft:plains
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Invalid BIOMES_O_PLENTY_BIOME"));
    }

    @Test
    void parseMappingsCsvRejectsWrongProviderNamespace() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,100,minecraft:plains,90,minecraft:plains
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Invalid REGIONS_UNEXPLORED_BIOME namespace"));
    }

    @Test
    void parseMappingsCsvRejectsConflictingDuplicateColor() {
        String csv = HEADER + """

            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,100,regions_unexplored:maple_forest,90,minecraft:forest
            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:origin_valley,100,regions_unexplored:maple_forest,90,minecraft:forest
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Conflicting biome ids"));
    }

    @Test
    void resolveMappingsAutoUsesLowestPriorityLoadedProvider() {
        Holder<Biome> bopHolder = dummyBiomeHolder();
        Holder<Biome> regionsHolder = dummyBiomeHolder();
        Holder<Biome> fallbackHolder = dummyBiomeHolder();

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(0x010203, selection(100, 90)),
            BiomeIntegrationMode.AUTO,
            Set.of(EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY, EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED),
            biomeId -> {
                if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                    return bopHolder;
                }
                if ("regions_unexplored:maple_forest".equals(biomeId.toString())) {
                    return regionsHolder;
                }
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                return dummyBiomeHolder();
            }
        );

        assertEquals(regionsHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void resolveMappingsAutoSkipsUnloadedProvider() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        Holder<Biome> regionsHolder = dummyBiomeHolder();

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(0x010203, selection(80, 90)),
            BiomeIntegrationMode.AUTO,
            Set.of(EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED),
            biomeId -> {
                if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                    throw new IllegalStateException("bop lookup should not happen when unloaded");
                }
                if ("regions_unexplored:maple_forest".equals(biomeId.toString())) {
                    return regionsHolder;
                }
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                return dummyBiomeHolder();
            }
        );

        assertEquals(regionsHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void resolveMappingsAutoFallsBackWhenNoProvidersLoaded() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(0x010203, selection(80, 90)),
            BiomeIntegrationMode.AUTO,
            Set.of(),
            biomeId -> {
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                if (biomeId.getNamespace().equals("minecraft")) {
                    return dummyBiomeHolder();
                }
                throw new IllegalStateException("provider lookup should not happen when none are loaded");
            }
        );

        assertEquals(fallbackHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void resolveMappingsInVanillaModeSkipsProviderLookup() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(0x010203, selection(100, 90)),
            BiomeIntegrationMode.VANILLA,
            Set.of(EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY, EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED),
            biomeId -> {
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                if (biomeId.getNamespace().equals("minecraft")) {
                    return dummyBiomeHolder();
                }
                throw new IllegalStateException("provider lookup should not happen in vanilla mode");
            }
        );

        assertEquals(fallbackHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void explicitProviderModeFailsFastWhenProviderBiomeMissing() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                Map.of(0x010203, selection(100, 90)),
                BiomeIntegrationMode.REGIONS_UNEXPLORED,
                Set.of(),
                biomeId -> {
                    if ("minecraft:plains".equals(biomeId.toString())) {
                        return dummyBiomeHolder();
                    }
                    if ("regions_unexplored:maple_forest".equals(biomeId.toString())) {
                        throw new IllegalStateException("missing");
                    }
                    return dummyBiomeHolder();
                }
            )
        );
        assertTrue(exception.getMessage().contains("Unknown preferred biome id"));
    }

    @Test
    void explicitProviderModeUsesFallbackWhenProviderColumnBlank() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        EcoregionBiomeMappings.BiomeSelectionIds selection =
            new EcoregionBiomeMappings.BiomeSelectionIds(Identifier.parse("minecraft:plains"), Map.of());

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(0x010203, selection),
            BiomeIntegrationMode.REGIONS_UNEXPLORED,
            Set.of(),
            biomeId -> {
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                return dummyBiomeHolder();
            }
        );

        assertEquals(fallbackHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void resolveMappingsFailsWhenPreferredBiomeHolderIsUnboundInAutoMode() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        Holder<Biome> unboundPreferredHolder = Holder.Reference.createStandAlone(
            new HolderOwner<>() {},
            ResourceKey.create(Registries.BIOME, Identifier.parse("regions_unexplored:maple_forest"))
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                Map.of(0x010203, selection(100, 90)),
                BiomeIntegrationMode.AUTO,
                Set.of(EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED),
                biomeId -> {
                    if ("minecraft:plains".equals(biomeId.toString())) {
                        return fallbackHolder;
                    }
                    if ("regions_unexplored:maple_forest".equals(biomeId.toString())) {
                        return unboundPreferredHolder;
                    }
                    return dummyBiomeHolder();
                }
            )
        );

        assertTrue(!unboundPreferredHolder.isBound());
        assertTrue(exception.getMessage().contains("Unknown preferred biome id"));
    }

    @Test
    void resolveMappingsFailsWhenFallbackBiomeMissing() {
        EcoregionBiomeMappings.BiomeSelectionIds selection =
            new EcoregionBiomeMappings.BiomeSelectionIds(
                Identifier.parse("minecraft:not_a_real_biome"),
                Map.of()
            );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                Map.of(0x010203, selection),
                BiomeIntegrationMode.VANILLA,
                Set.of(),
                biomeId -> {
                    throw new IllegalStateException("missing fallback");
                }
            )
        );
        assertTrue(exception.getMessage().contains("Unknown fallback biome id"));
    }

    @Test
    void startupMappingLoadsExpectedGeneratedRows() {
        assertEquals(847, EcoregionBiomeMappings.requireColorToBiomeIds().size());
    }

    @Test
    void startupValidationAcceptsGeneratedMapping() {
        assertDoesNotThrow(EcoregionBiomeMappings::validateStartupBiomeMapping);
    }

    @Test
    void resolvedPossibleBiomesIncludeRiverAndFrozenRiver() {
        Map<Identifier, Holder<Biome>> holdersById = new java.util.HashMap<>();
        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(
                0x123456,
                new EcoregionBiomeMappings.BiomeSelectionIds(
                    Identifier.parse("minecraft:plains"),
                    Map.of()
                )
            ),
            BiomeIntegrationMode.AUTO,
            Set.of(),
            biomeId -> holdersById.computeIfAbsent(biomeId, ignored -> dummyBiomeHolder())
        );

        assertTrue(resolved.possibleBiomes().contains(resolved.riverBiome()));
        assertTrue(resolved.possibleBiomes().contains(resolved.frozenRiverBiome()));
    }

    private static EcoregionBiomeMappings.BiomeSelectionIds selection(int bopPriority, int regionsPriority) {
        return new EcoregionBiomeMappings.BiomeSelectionIds(
            Identifier.parse("minecraft:plains"),
            Map.of(
                EcoregionBiomeMappings.BiomeProvider.BIOMES_O_PLENTY,
                new EcoregionBiomeMappings.ProviderBiome(Identifier.parse("biomesoplenty:maple_woods"), bopPriority),
                EcoregionBiomeMappings.BiomeProvider.REGIONS_UNEXPLORED,
                new EcoregionBiomeMappings.ProviderBiome(Identifier.parse("regions_unexplored:maple_forest"), regionsPriority)
            )
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Holder<Biome> dummyBiomeHolder() {
        return (Holder<Biome>) (Holder) Holder.direct(new Object());
    }
}
