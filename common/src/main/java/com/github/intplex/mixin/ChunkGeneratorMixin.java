package com.github.intplex.mixin;

import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.registries.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @Redirect(
        method = "applyBiomeDecoration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean terrariumExpanded$filterLavaLakes(
        PlacedFeature placedFeature,
        WorldGenLevel worldGenLevel,
        ChunkGenerator chunkGenerator,
        RandomSource randomSource,
        BlockPos origin
    ) {
        ChunkGenerator generator = (ChunkGenerator) (Object) this;
        if (!(generator.getBiomeSource() instanceof EcoregionBiomeSource earthBiomeSource)) {
            return placedFeature.placeWithBiomeCheck(worldGenLevel, chunkGenerator, randomSource, origin);
        }

        EarthWorldgenToggles toggles = earthBiomeSource.worldgenToggles();
        if (
            !toggles.lavaAquifers() &&
            isLavaLakeFeature(placedFeature, worldGenLevel.registryAccess())
        ) {
            return false;
        }

        return placedFeature.placeWithBiomeCheck(worldGenLevel, chunkGenerator, randomSource, origin);
    }

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void terrariumExpanded$filterStructureGeneration(
        StructureSet.StructureSelectionEntry structureSelectionEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        long levelSeed,
        ChunkAccess chunkAccess,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ChunkGenerator generator = (ChunkGenerator) (Object) this;
        if (!(generator.getBiomeSource() instanceof EcoregionBiomeSource earthBiomeSource)) {
            return;
        }

        EarthWorldgenToggles toggles = earthBiomeSource.worldgenToggles();
        Optional<ResourceKey<Structure>> structureKey = structureSelectionEntry.structure().unwrapKey();
        if (structureKey.isEmpty()) {
            return;
        }

        if (!toggles.allowsStructure(structureKey.get().location())) {
            cir.setReturnValue(false);
        }
    }

    private static boolean isLavaLakeFeature(PlacedFeature placedFeature, RegistryAccess registryAccess) {
        Registry<PlacedFeature> placedFeatureRegistry = registryAccess.registryOrThrow(Registries.PLACED_FEATURE);
        Optional<ResourceKey<PlacedFeature>> placedFeatureKey = placedFeatureRegistry.getResourceKey(placedFeature);
        if (placedFeatureKey.isEmpty()) {
            return false;
        }

        String path = placedFeatureKey.get().location().getPath();
        return path.equals("lake_lava_underground") || path.equals("lake_lava_surface");
    }
}
