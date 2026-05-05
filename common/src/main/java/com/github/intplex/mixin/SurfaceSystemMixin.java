package com.github.intplex.mixin;

import com.github.intplex.earth.terrain.EarthSurfaceRuleGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(SurfaceSystem.class)
abstract class SurfaceSystemMixin {
    @WrapOperation(
        method = "buildSurface",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState terrariumExpanded$skipInteriorCaveSurfaceRules(
        @Coerce Object surfaceRule,
        int blockX,
        int blockY,
        int blockZ,
        Operation<BlockState> original,
        RandomState randomState,
        BiomeManager biomeManager,
        Registry<Biome> biomeRegistry,
        boolean useLegacyRandomSource,
        WorldGenerationContext worldGenerationContext,
        ChunkAccess chunkAccess,
        NoiseChunk noiseChunk,
        SurfaceRules.RuleSource ruleSource
    ) {
        if (EarthSurfaceRuleGuard.shouldSkipSurfaceRule(chunkAccess, blockX, blockY, blockZ)) {
            return null;
        }
        return original.call(surfaceRule, blockX, blockY, blockZ);
    }
}
