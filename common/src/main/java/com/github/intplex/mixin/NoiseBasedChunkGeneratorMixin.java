package com.github.intplex.mixin;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.EarthAirCarverPolicy;
import com.github.intplex.earth.terrain.EarthAquiferNoiseRouter;
import com.github.intplex.earth.terrain.EarthFluidPicker;
import com.github.intplex.earth.terrain.EarthSurfaceWaterCavePostProcessor;
import com.github.intplex.earth.terrain.EarthTerrainEnvelopePostProcessor;
import com.github.intplex.earth.terrain.EarthWorldgenToggles;
import com.github.intplex.earth.terrain.EarthSurfaceRuleGuard;
import com.github.intplex.earth.terrain.InlandWaterChunkPostProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Redirect(
        method = "applyCarvers",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/BiomeGenerationSettings;getCarvers()Ljava/lang/Iterable;"
        )
    )
    private Iterable<Holder<ConfiguredWorldCarver<?>>> terrariumExpanded$filterCarvers(
        BiomeGenerationSettings biomeGenerationSettings
    ) {
        Iterable<Holder<ConfiguredWorldCarver<?>>> original = biomeGenerationSettings.getCarvers();

        EarthWorldgenToggles toggles = earthWorldgenToggles();
        if (toggles == null) {
            return original;
        }

        List<Holder<ConfiguredWorldCarver<?>>> filtered = new ArrayList<>();
        boolean changed = false;
        for (Holder<ConfiguredWorldCarver<?>> holder : original) {
            if (EarthAirCarverPolicy.shouldKeep(holder, toggles)) {
                filtered.add(holder);
            } else {
                changed = true;
            }
        }
        return changed ? filtered : original;
    }

    @Inject(method = "applyCarvers", at = @At("TAIL"))
    private void terrariumExpanded$floodSurfaceConnectedCaves(
        WorldGenRegion worldGenRegion,
        long levelSeed,
        RandomState randomState,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunkAccess,
        CallbackInfo ci
    ) {
        EcoregionBiomeSource earthBiomeSource = earthBiomeSource();
        if (earthBiomeSource == null) {
            return;
        }

        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        BlockState surfaceFluid = generator.generatorSettings().value().defaultFluid();
        boolean includeInlandWater = InlandWaterChunkPostProcessor.shouldProcess(generator);

        // Carvers run after the initial noise-envelope pass. Reassert the
        // surface water first, then flood only cave air connected to it.
        EarthTerrainEnvelopePostProcessor.enforceChunk(
            chunkAccess,
            earthBiomeSource.seaLevel(),
            surfaceFluid
        );
        if (includeInlandWater) {
            InlandWaterChunkPostProcessor.fillChunk(chunkAccess);
        }
        EarthSurfaceWaterCavePostProcessor.floodSurfaceConnectedCaves(
            chunkAccess,
            earthBiomeSource.seaLevel(),
            surfaceFluid,
            includeInlandWater
        );
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
        EarthGenConfig.setActiveMaxTerrainY(
            EarthGenConfig.maxTerrainYFromVerticalRange(chunkAccess.getMinY(), chunkAccess.getHeight())
        );

        EcoregionBiomeSource earthBiomeSource = earthBiomeSource();
        EarthWorldgenToggles toggles = earthBiomeSource == null ? null : earthBiomeSource.worldgenToggles();
        if (toggles == null) {
            return NoiseChunk.forChunk(chunkAccess, randomState, beardifierOrMarker, settings, fluidPicker, blender);
        }

        NoiseSettings sourceNoiseSettings = settings.noiseSettings();
        NoiseSettings adjustedNoiseSettings = terrariumExpanded$noiseSettingsForChunk(settings, chunkAccess);

        boolean aquifersEnabled = toggles.aquifers();
        NoiseGeneratorSettings adjustedSettings = settings;
        int seaLevel = earthBiomeSource.seaLevel();
        boolean noiseSettingsChanged = !adjustedNoiseSettings.equals(sourceNoiseSettings);
        if (
            noiseSettingsChanged
                || settings.seaLevel() != seaLevel
                || settings.aquifersEnabled() != aquifersEnabled
                || !settings.oreVeinsEnabled()
        ) {
            adjustedSettings = new NoiseGeneratorSettings(
                adjustedNoiseSettings,
                settings.defaultBlock(),
                settings.defaultFluid(),
                settings.noiseRouter(),
                settings.surfaceRule(),
                settings.spawnTarget(),
                seaLevel,
                settings.disableMobGeneration(),
                aquifersEnabled,
                true,
                settings.useLegacyRandomSource()
            );
        }

        Aquifer.FluidPicker adjustedFluidPicker = fluidPicker;
        if (!aquifersEnabled) {
            adjustedFluidPicker = EarthFluidPicker.create(
                fluidPicker,
                adjustedSettings.seaLevel(),
                adjustedSettings.defaultFluid(),
                true
            );
        } else if (!toggles.lavaAquifers()) {
            Aquifer.FluidStatus waterOnly = new Aquifer.FluidStatus(
                adjustedSettings.seaLevel(),
                adjustedSettings.defaultFluid()
            );
            adjustedFluidPicker = (x, y, z) -> waterOnly;
        } else if (settings.seaLevel() != adjustedSettings.seaLevel()) {
            adjustedFluidPicker = EarthFluidPicker.create(
                fluidPicker,
                adjustedSettings.seaLevel(),
                adjustedSettings.defaultFluid(),
                false
            );
        }

        NoiseGeneratorSettings finalSettings = adjustedSettings;
        Aquifer.FluidPicker finalFluidPicker = adjustedFluidPicker;
        if (aquifersEnabled && !toggles.lavaAquifers()) {
            return EarthAquiferNoiseRouter.withLavaDisabled(() -> NoiseChunk.forChunk(
                chunkAccess,
                randomState,
                beardifierOrMarker,
                finalSettings,
                finalFluidPicker,
                blender
            ));
        }
        return NoiseChunk.forChunk(
            chunkAccess,
            randomState,
            beardifierOrMarker,
            finalSettings,
            finalFluidPicker,
            blender
        );
    }

    @Redirect(
        method = "fillFromNoise",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;noiseSettings()Lnet/minecraft/world/level/levelgen/NoiseSettings;"
        )
    )
    private NoiseSettings terrariumExpanded$useExpandedNoiseSettingsDuringChunkFill(
        NoiseGeneratorSettings settings,
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        ChunkAccess chunkAccess
    ) {
        if (earthWorldgenToggles() == null) {
            return settings.noiseSettings();
        }
        return terrariumExpanded$noiseSettingsForChunk(settings, chunkAccess);
    }

    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void terrariumExpanded$postProcessAfterFillFromNoise(
        Blender blender,
        RandomState randomState,
        StructureManager structureManager,
        ChunkAccess chunkAccess,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        EcoregionBiomeSource earthBiomeSource = earthBiomeSource();
        if (earthBiomeSource == null) {
            return;
        }

        cir.setReturnValue(cir.getReturnValue().thenApply(chunk -> {
            EarthTerrainEnvelopePostProcessor.enforceChunk(
                chunk,
                earthBiomeSource.seaLevel(),
                generator.generatorSettings().value().defaultFluid()
            );
            if (InlandWaterChunkPostProcessor.shouldProcess(generator)) {
                InlandWaterChunkPostProcessor.fillChunk(chunk);
            }
            return chunk;
        }));
    }

    @Redirect(
        method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V"
        )
    )
    private void terrariumExpanded$guardEarthSurfaceRules(
        SurfaceSystem surfaceSystem,
        RandomState randomState,
        BiomeManager biomeManager,
        Registry<Biome> biomeRegistry,
        boolean useLegacyRandomSource,
        WorldGenerationContext worldGenerationContext,
        ChunkAccess surfaceChunkAccess,
        NoiseChunk noiseChunk,
        SurfaceRules.RuleSource ruleSource,
        ChunkAccess chunkAccess,
        WorldGenerationContext ignoredWorldGenerationContext,
        RandomState ignoredRandomState,
        StructureManager structureManager,
        BiomeManager ignoredBiomeManager,
        Registry<Biome> ignoredBiomeRegistry,
        Blender blender
    ) {
        EarthWorldgenToggles toggles = earthWorldgenToggles();
        if (toggles != null && toggles.caves()) {
            EarthSurfaceRuleGuard.runForChunk(surfaceChunkAccess, () -> surfaceSystem.buildSurface(
                randomState,
                biomeManager,
                biomeRegistry,
                useLegacyRandomSource,
                worldGenerationContext,
                surfaceChunkAccess,
                noiseChunk,
                ruleSource
            ));
            return;
        }

        surfaceSystem.buildSurface(
            randomState,
            biomeManager,
            biomeRegistry,
            useLegacyRandomSource,
            worldGenerationContext,
            surfaceChunkAccess,
            noiseChunk,
            ruleSource
        );
    }

    @Inject(method = "getSeaLevel", at = @At("HEAD"), cancellable = true)
    private void terrariumExpanded$useEarthSeaLevel(CallbackInfoReturnable<Integer> cir) {
        EcoregionBiomeSource earthBiomeSource = earthBiomeSource();
        if (earthBiomeSource != null) {
            cir.setReturnValue(earthBiomeSource.seaLevel());
        }
    }

    private EarthWorldgenToggles earthWorldgenToggles() {
        EcoregionBiomeSource earthBiomeSource = earthBiomeSource();
        return earthBiomeSource == null ? null : earthBiomeSource.worldgenToggles();
    }

    private EcoregionBiomeSource earthBiomeSource() {
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) (Object) this;
        BiomeSource biomeSource = generator.getBiomeSource();
        if (biomeSource instanceof EcoregionBiomeSource earthBiomeSource) {
            return earthBiomeSource;
        }
        return null;
    }

    private static NoiseSettings terrariumExpanded$noiseSettingsForChunk(NoiseGeneratorSettings settings, ChunkAccess chunkAccess) {
        NoiseSettings sourceNoiseSettings = settings.noiseSettings();
        int chunkMinY = chunkAccess.getMinY();
        int chunkHeight = chunkAccess.getHeight();
        if (sourceNoiseSettings.minY() == chunkMinY && sourceNoiseSettings.height() == chunkHeight) {
            return sourceNoiseSettings;
        }
        return NoiseSettings.create(
            chunkMinY,
            chunkHeight,
            sourceNoiseSettings.noiseSizeHorizontal(),
            sourceNoiseSettings.noiseSizeVertical()
        );
    }

}
