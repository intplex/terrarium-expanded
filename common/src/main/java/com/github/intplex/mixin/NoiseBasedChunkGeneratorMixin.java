package com.github.intplex.mixin;

import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import com.github.intplex.earth.terrain.InlandWaterChunkPostProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Redirect(
        method = "applyCarvers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/BiomeGenerationSettings;getCarvers(Lnet/minecraft/world/level/levelgen/GenerationStep$Carving;)Ljava/lang/Iterable;"
        )
    )
    private Iterable<Holder<ConfiguredWorldCarver<?>>> terrariumExpanded$filterCarvers(
        BiomeGenerationSettings biomeGenerationSettings,
        GenerationStep.Carving carvingStep
    ) {
        Iterable<Holder<ConfiguredWorldCarver<?>>> original = biomeGenerationSettings.getCarvers(carvingStep);
        if (carvingStep != GenerationStep.Carving.AIR) {
            return original;
        }

        EarthWorldgenToggles toggles = earthWorldgenToggles();
        if (toggles == null) {
            return original;
        }

        List<Holder<ConfiguredWorldCarver<?>>> filtered = new ArrayList<>();
        boolean changed = false;
        for (Holder<ConfiguredWorldCarver<?>> holder : original) {
            if (shouldKeepCarver(holder, toggles)) {
                filtered.add(holder);
            } else {
                changed = true;
            }
        }
        return changed ? filtered : original;
    }

    @Redirect(
        method = "applyCarvers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;aquifer()Lnet/minecraft/world/level/levelgen/Aquifer;"
        )
    )
    private Aquifer terrariumExpanded$preserveWaterDuringCaveCarving(
        NoiseChunk noiseChunk,
        WorldGenRegion worldGenRegion,
        long levelSeed,
        RandomState randomState,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunkAccess,
        GenerationStep.Carving carvingStep
    ) {
        Aquifer aquifer = noiseChunk.aquifer();
        if (carvingStep != GenerationStep.Carving.AIR) {
            return aquifer;
        }

        EarthWorldgenToggles toggles = earthWorldgenToggles();
        if (toggles == null || !toggles.caves() || toggles.aquifers()) {
            return aquifer;
        }

        return boundaryAwareDryCaveAquifer(aquifer, chunkAccess);
    }

    @Redirect(
        method = "createNoiseChunk",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;forChunk(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/DensityFunctions$BeardifierOrMarker;Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;Lnet/minecraft/world/level/levelgen/blending/Blender;)Lnet/minecraft/world/level/levelgen/NoiseChunk;"
        )
    )
    private NoiseChunk terrariumExpanded$applyPresetNoiseToggles(
        ChunkAccess chunkAccess,
        RandomState randomState,
        DensityFunctions.BeardifierOrMarker beardifierOrMarker,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Blender blender
    ) {
        EarthWorldgenToggles toggles = earthWorldgenToggles();
        if (toggles == null) {
            return NoiseChunk.forChunk(chunkAccess, randomState, beardifierOrMarker, settings, fluidPicker, blender);
        }

        NoiseGeneratorSettings adjustedSettings = settings;
        boolean aquifersEnabled = toggles.aquifers();
        if (settings.aquifersEnabled() != aquifersEnabled || !settings.oreVeinsEnabled()) {
            adjustedSettings = new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                settings.noiseRouter(),
                settings.surfaceRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                aquifersEnabled,
                true,
                settings.useLegacyRandomSource()
            );
        }

        Aquifer.FluidPicker adjustedFluidPicker = fluidPicker;
        if (aquifersEnabled && !toggles.lavaAquifers()) {
            Aquifer.FluidStatus waterOnly = new Aquifer.FluidStatus(
                adjustedSettings.seaLevel(),
                adjustedSettings.defaultFluid()
            );
            adjustedFluidPicker = (x, y, z) -> waterOnly;
        }

        return NoiseChunk.forChunk(
            chunkAccess,
            randomState,
            beardifierOrMarker,
            adjustedSettings,
            adjustedFluidPicker,
            blender
        );
    }

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void terrariumExpanded$fillInlandWater(
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        ChunkAccess chunkAccess,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        if (!InlandWaterChunkPostProcessor.shouldProcess(generator)) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue().thenApply(chunk -> {
            InlandWaterChunkPostProcessor.fillChunk(chunk);
            return chunk;
        }));
    }

    private EarthWorldgenToggles earthWorldgenToggles() {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        BiomeSource biomeSource = generator.getBiomeSource();
        if (biomeSource instanceof EcoregionBiomeSource earthBiomeSource) {
            return earthBiomeSource.worldgenToggles();
        }
        return null;
    }

    private static boolean shouldKeepCarver(Holder<ConfiguredWorldCarver<?>> holder, EarthWorldgenToggles toggles) {
        Optional<ResourceKey<ConfiguredWorldCarver<?>>> key = holder.unwrapKey();
        if (key.isEmpty()) {
            return true;
        }
        String path = key.get().location().getPath();
        if (path.equals("cave")) {
            return toggles.caves();
        }
        if (path.equals("canyon")) {
            return toggles.canyons();
        }
        if (path.equals("cave_extra_underground")) {
            return toggles.extraUnderground();
        }
        return true;
    }

    private static Aquifer boundaryAwareDryCaveAquifer(Aquifer delegate, ChunkAccess chunkAccess) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborMutableBlockPos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        return new Aquifer() {
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double density) {
                BlockState carvedState = delegate.computeSubstance(functionContext, density);
                if (carvedState == null) {
                    return carvedState;
                }

                mutableBlockPos.set(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ());
                BlockState adjacentFluidState = adjacentFluidState(
                    chunkAccess,
                    mutableBlockPos,
                    neighborMutableBlockPos
                );

                if (carvedState.getFluidState().isEmpty()) {
                    return adjacentFluidState != null ? adjacentFluidState : carvedState;
                }

                return adjacentFluidState != null ? carvedState : air;
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return true;
            }
        };
    }

    private static BlockState adjacentFluidState(
        ChunkAccess chunkAccess,
        BlockPos.MutableBlockPos center,
        BlockPos.MutableBlockPos mutableBlockPos
    ) {
        for (Direction direction : Direction.values()) {
            mutableBlockPos.setWithOffset(center, direction);
            BlockState neighborState = chunkAccess.getBlockState(mutableBlockPos);
            if (!neighborState.getFluidState().isEmpty()) {
                return neighborState.getFluidState().createLegacyBlock();
            }
        }
        return null;
    }
}
