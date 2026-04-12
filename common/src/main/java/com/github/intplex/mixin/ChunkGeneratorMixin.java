package com.github.intplex.mixin;

import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
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
}
