package com.github.intplex.neoforge;

import com.github.intplex.TerrariumExpanded;
import com.github.intplex.earth.EarthCommands;
import com.github.intplex.earth.EarthSpawnManager;
import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.ContinentalDensityFunction;
import com.github.intplex.earth.terrain.DepthDensityFunction;
import com.github.intplex.earth.terrain.EarthSurfaceCavesDensityFunction;
import com.github.intplex.earth.terrain.EnvelopeDensityFunction;
import com.github.intplex.earth.terrain.ErosionDensityFunction;
import com.github.intplex.earth.terrain.TerrainServices;
import com.github.intplex.earth.terrain.WeirdnessDensityFunction;
import com.github.intplex.earth.terrain.WorldgenPlayerDiagnostics;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TerrariumExpanded.MOD_ID)
public final class TerrariumExpandedNeoForge {
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, TerrariumExpanded.MOD_ID);

    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, TerrariumExpanded.MOD_ID);

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<EcoregionBiomeSource>> ECOREGION_TILES =
        BIOME_SOURCES.register("ecoregion_tiles", () -> EcoregionBiomeSource.CODEC);

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<EnvelopeDensityFunction>> TERRAIN_ENVELOPE =
        DENSITY_FUNCTION_TYPES.register("terrain_envelope", () -> EnvelopeDensityFunction.CODEC.codec());

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<EarthSurfaceCavesDensityFunction>> EARTH_SURFACE_CAVES =
        DENSITY_FUNCTION_TYPES.register("earth_surface_caves", () -> EarthSurfaceCavesDensityFunction.CODEC.codec());

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<ContinentalDensityFunction>> TERRAIN_CONTINENTALNESS =
        DENSITY_FUNCTION_TYPES.register("terrain_continentalness", () -> ContinentalDensityFunction.CODEC.codec());

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<ErosionDensityFunction>> TERRAIN_EROSION =
        DENSITY_FUNCTION_TYPES.register("terrain_erosion", () -> ErosionDensityFunction.CODEC.codec());

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<DepthDensityFunction>> TERRAIN_DEPTH =
        DENSITY_FUNCTION_TYPES.register("terrain_depth", () -> DepthDensityFunction.CODEC.codec());

    @SuppressWarnings("unused")
    private static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<WeirdnessDensityFunction>> TERRAIN_WEIRDNESS =
        DENSITY_FUNCTION_TYPES.register("terrain_weirdness", () -> WeirdnessDensityFunction.CODEC.codec());

    public TerrariumExpandedNeoForge(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        TerrariumExpanded.init(FMLPaths.GAMEDIR.get());
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        EarthCommands.register(event.getDispatcher());
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        EarthSpawnManager.forceSpawnFromPreset(event.getServer(), "neoforge_server_about_to_start");
    }

    private void onServerStarting(ServerStartingEvent event) {
        EarthSpawnManager.forceSpawnFromPreset(event.getServer(), "neoforge_server_starting");
    }

    private void onServerStarted(ServerStartedEvent event) {
        EarthSpawnManager.forceSpawnFromPreset(event.getServer(), "neoforge_server_started");
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        WorldgenPlayerDiagnostics.updateFromServer(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        TerrainServices.shutdown();
    }
}
