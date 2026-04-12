package com.github.intplex.fabric;

import com.github.intplex.TerrariumExpanded;
import com.github.intplex.earth.EarthCommands;
import com.github.intplex.earth.EarthSpawnManager;
import com.github.intplex.earth.biome.EcoregionBiomeSource;
import com.github.intplex.earth.terrain.ContinentalDensityFunction;
import com.github.intplex.earth.terrain.DepthDensityFunction;
import com.github.intplex.earth.terrain.EnvelopeDensityFunction;
import com.github.intplex.earth.terrain.ErosionDensityFunction;
import com.github.intplex.earth.terrain.TerrainServices;
import com.github.intplex.earth.terrain.WeirdnessDensityFunction;
import com.github.intplex.earth.terrain.WorldgenPlayerDiagnostics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class TerrariumExpandedFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(
            BuiltInRegistries.BIOME_SOURCE,
            TerrariumExpanded.id("ecoregion_tiles"),
            EcoregionBiomeSource.CODEC
        );
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrariumExpanded.id("terrain_envelope"),
            EnvelopeDensityFunction.CODEC.codec()
        );
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrariumExpanded.id("terrain_continentalness"),
            ContinentalDensityFunction.CODEC.codec()
        );
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrariumExpanded.id("terrain_erosion"),
            ErosionDensityFunction.CODEC.codec()
        );
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrariumExpanded.id("terrain_depth"),
            DepthDensityFunction.CODEC.codec()
        );
        Registry.register(
            BuiltInRegistries.DENSITY_FUNCTION_TYPE,
            TerrariumExpanded.id("terrain_weirdness"),
            WeirdnessDensityFunction.CODEC.codec()
        );
        ServerLifecycleEvents.SERVER_STARTING.register(server -> EarthSpawnManager.forceSpawnFromPreset(server, "fabric_server_starting"));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> EarthSpawnManager.forceSpawnFromPreset(server, "fabric_server_started"));
        ServerTickEvents.END_SERVER_TICK.register(WorldgenPlayerDiagnostics::updateFromServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TerrainServices.shutdown());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EarthCommands.register(dispatcher));
        TerrariumExpanded.init(FabricLoader.getInstance().getGameDir());
    }
}
