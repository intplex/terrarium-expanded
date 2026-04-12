package com.github.intplex.earth.biome;

import java.io.StringReader;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcoregionBiomeMappingsTest {
    @Test
    void effectiveIntegrationModeUsesVanillaWhenAutoAndBopMissing() {
        assertEquals(
            BiomeIntegrationMode.VANILLA,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.AUTO, false)
        );
    }

    @Test
    void effectiveIntegrationModeKeepsAutoWhenBopPresent() {
        assertEquals(
            BiomeIntegrationMode.AUTO,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.AUTO, true)
        );
    }

    @Test
    void effectiveIntegrationModeKeepsExplicitExpandedWhenBopMissing() {
        assertEquals(
            BiomeIntegrationMode.EXPANDED,
            EcoregionBiomeMappings.effectiveIntegrationMode(BiomeIntegrationMode.EXPANDED, false)
        );
    }

    @Test
    void parseMappingsCsvParsesValidRows() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,,minecraft:plains
            #AABBCC,Other Eco,Other Biome,Palearctic,biomesoplenty:maple_woods,minecraft:forest
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings = EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));

        assertEquals(2, mappings.size());
        assertEquals(ResourceLocation.parse("minecraft:plains"), mappings.get(0x112233).preferredBiomeId());
        assertEquals(ResourceLocation.parse("minecraft:plains"), mappings.get(0x112233).fallbackBiomeId());
        assertEquals(ResourceLocation.parse("biomesoplenty:maple_woods"), mappings.get(0xAABBCC).preferredBiomeId());
        assertEquals(ResourceLocation.parse("minecraft:forest"), mappings.get(0xAABBCC).fallbackBiomeId());
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
        assertTrue(
            exception.getMessage().contains(
                "expected UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME"
            )
        );
    }

    @Test
    void parseMappingsCsvRejectsBlankBiome() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Blank MINECRAFT_BIOME"));
    }

    @Test
    void parseMappingsCsvAcceptsBlankBopBiome() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,,minecraft:plains
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings =
            EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));
        assertEquals(ResourceLocation.parse("minecraft:plains"), mappings.get(0x112233).preferredBiomeId());
        assertEquals(ResourceLocation.parse("minecraft:plains"), mappings.get(0x112233).fallbackBiomeId());
    }

    @Test
    void parseMappingsCsvHandlesQuotedBiomeNameWithComma() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,"Mediterranean Forests, Woodlands & Scrub",Nearctic,biomesoplenty:maple_woods,minecraft:forest
            """;

        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> mappings =
            EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv));
        assertEquals(ResourceLocation.parse("biomesoplenty:maple_woods"), mappings.get(0x112233).preferredBiomeId());
        assertEquals(ResourceLocation.parse("minecraft:forest"), mappings.get(0x112233).fallbackBiomeId());
    }

    @Test
    void parseMappingsCsvRejectsInvalidBopBiome() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,not a biome,minecraft:plains
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Invalid BIOMES_O_PLENTY_BIOME"));
    }

    @Test
    void parseMappingsCsvRejectsNonBopNamespaceInPreferredBiomeColumn() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,minecraft:plains,minecraft:plains
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Invalid BIOMES_O_PLENTY_BIOME namespace"));
    }

    @Test
    void parseMappingsCsvRejectsConflictingDuplicateColor() {
        String csv = """
            UNIQUE_ECOREGION_COLOR,ECO_NAME,BIOME_NAME,REALM,BIOMES_O_PLENTY_BIOME,MINECRAFT_BIOME
            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:maple_woods,minecraft:forest
            #112233,Test Eco,Test Biome,Nearctic,biomesoplenty:origin_valley,minecraft:forest
            """;

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.parseMappingsCsv(new StringReader(csv))
        );
        assertTrue(exception.getMessage().contains("Conflicting biome ids"));
    }

    @Test
    void resolveMappingsFailsWhenPreferredBiomeMissingInAutoMode() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> byColor = Map.of(
            0x010203,
            new EcoregionBiomeMappings.BiomeSelectionIds(
                ResourceLocation.parse("biomesoplenty:maple_woods"),
                ResourceLocation.parse("minecraft:plains")
            )
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                byColor,
                BiomeIntegrationMode.AUTO,
                biomeId -> {
                    if ("minecraft:plains".equals(biomeId.toString())) {
                        return fallbackHolder;
                    }
                    if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                        throw new IllegalStateException("missing");
                    }
                    return dummyBiomeHolder();
                }
            )
        );
        assertTrue(exception.getMessage().contains("Unknown preferred biome id"));
    }

    @Test
    void resolveMappingsFailsWhenPreferredBiomeHolderIsUnboundInAutoMode() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        Holder<Biome> unboundPreferredHolder = Holder.Reference.createStandAlone(
            new HolderOwner<>() {},
            ResourceKey.create(Registries.BIOME, ResourceLocation.parse("biomesoplenty:maple_woods"))
        );
        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> byColor = Map.of(
            0x010203,
            new EcoregionBiomeMappings.BiomeSelectionIds(
                ResourceLocation.parse("biomesoplenty:maple_woods"),
                ResourceLocation.parse("minecraft:plains")
            )
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                byColor,
                BiomeIntegrationMode.AUTO,
                biomeId -> {
                    if ("minecraft:plains".equals(biomeId.toString())) {
                        return fallbackHolder;
                    }
                    if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
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
    void resolveMappingsInVanillaModeSkipsPreferredBiomeLookup() {
        Holder<Biome> fallbackHolder = dummyBiomeHolder();
        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> byColor = Map.of(
            0x010203,
            new EcoregionBiomeMappings.BiomeSelectionIds(
                ResourceLocation.parse("biomesoplenty:maple_woods"),
                ResourceLocation.parse("minecraft:plains")
            )
        );

        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            byColor,
            BiomeIntegrationMode.VANILLA,
            biomeId -> {
                if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                    throw new IllegalStateException("preferred lookup should not happen in vanilla mode");
                }
                if ("minecraft:plains".equals(biomeId.toString())) {
                    return fallbackHolder;
                }
                return dummyBiomeHolder();
            }
        );

        assertEquals(fallbackHolder, resolved.byColor().get(0x010203));
    }

    @Test
    void resolveMappingsFailsWhenFallbackBiomeMissing() {
        Map<Integer, EcoregionBiomeMappings.BiomeSelectionIds> byColor = Map.of(
            0x010203,
            new EcoregionBiomeMappings.BiomeSelectionIds(
                ResourceLocation.parse("biomesoplenty:maple_woods"),
                ResourceLocation.parse("minecraft:not_a_real_biome")
            )
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> EcoregionBiomeMappings.resolveMappings(
                byColor,
                BiomeIntegrationMode.VANILLA,
                biomeId -> {
                    if ("biomesoplenty:maple_woods".equals(biomeId.toString())) {
                        throw new IllegalStateException("missing");
                    }
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
        Map<ResourceLocation, Holder<Biome>> holdersById = new java.util.HashMap<>();
        EcoregionBiomeMappings.ResolvedBiomeMapping resolved = EcoregionBiomeMappings.resolveMappings(
            Map.of(
                0x123456,
                new EcoregionBiomeMappings.BiomeSelectionIds(
                    ResourceLocation.parse("minecraft:plains"),
                    ResourceLocation.parse("minecraft:plains")
                )
            ),
            BiomeIntegrationMode.AUTO,
            biomeId -> holdersById.computeIfAbsent(biomeId, ignored -> dummyBiomeHolder())
        );

        assertTrue(resolved.possibleBiomes().contains(resolved.riverBiome()));
        assertTrue(resolved.possibleBiomes().contains(resolved.frozenRiverBiome()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Holder<Biome> dummyBiomeHolder() {
        return (Holder<Biome>) (Holder) Holder.direct(new Object());
    }
}
