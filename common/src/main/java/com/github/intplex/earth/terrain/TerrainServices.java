package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import com.github.intplex.earth.biome.EcoregionBiomeMappings;
import java.nio.file.Path;
import java.util.Map;

public final class TerrainServices {
    private static volatile Path gameDir;
    private static volatile EarthRuntimeContext runtimeContext;
    private static volatile TerrariumRuntimeConfig runtimeConfig = TerrariumRuntimeConfig.defaults();
    private static volatile long runtimeGeneration;

    private TerrainServices() {
    }

    public static synchronized void bootstrap(Path gameDir) {
        TerrainServices.gameDir = gameDir;
        TerrainServices.runtimeConfig = TerrariumRuntimeConfig.load(gameDir);
        BadTerrainTileRegistry.initialize(gameDir);
        syncEarthProfile(EarthGenerationProfile.defaults());
        EcoregionBiomeMappings.validateStartupBiomeMapping();
        BadTerrainTileRegistry.validateStartupRegistry();
    }

    public static TerrariumTileService tileService() {
        syncEarthZoom(EarthGenConfig.activeZoom());
        return requireService(requireContext().services().tileService(), "terrain");
    }

    public static TerrariumTileService recoveryTileService() {
        return requireService(requireContext().services().recoveryTileService(), "bathymetry recovery");
    }

    public static EcoregionTileService ecoregionTileService() {
        return requireService(requireContext().services().ecoregionTileService(), "ecoregion");
    }

    public static SurfaceWaterTileService surfaceWaterTileService() {
        syncEarthZoom(EarthGenConfig.activeZoom());
        return requireService(requireContext().services().surfaceWaterTileService(), "surface water");
    }

    static EarthRuntimeContext requireContext() {
        EarthRuntimeContext current = runtimeContext;
        if (current != null) {
            return current;
        }
        synchronized (TerrainServices.class) {
            current = runtimeContext;
            if (current == null) {
                syncEarthProfile(
                    EarthGenConfig.activeZoom(),
                    EarthGenConfig.activeMaxMountainY(),
                    EarthGenConfig.activeOceanFloorY(),
                    EarthGenConfig.activeSeaLevel(),
                    EarthGenConfig.activeBelowSeaHeightMode(),
                    EarthGenConfig.activeAboveSeaHeightMode()
                );
                current = runtimeContext;
            }
        }
        if (current == null) {
            throw new IllegalStateException("Terrain services not initialized");
        }
        return current;
    }

    public static synchronized void syncEarthZoom(int zoom) {
        syncEarthProfile(
            zoom,
            EarthGenConfig.activeMaxMountainY(),
            EarthGenConfig.activeOceanFloorY(),
            EarthGenConfig.activeSeaLevel(),
            EarthGenConfig.activeBelowSeaHeightMode(),
            EarthGenConfig.activeAboveSeaHeightMode()
        );
    }

    public static synchronized void syncEarthSettings(int zoom, int maxMountainY, int oceanFloorY) {
        syncEarthProfile(
            zoom,
            maxMountainY,
            oceanFloorY,
            EarthGenConfig.activeSeaLevel(),
            EarthGenConfig.activeBelowSeaHeightMode(),
            EarthGenConfig.activeAboveSeaHeightMode()
        );
    }

    public static synchronized void syncEarthSettings(int zoom, int maxMountainY, int oceanFloorY, int seaLevel) {
        syncEarthProfile(
            zoom,
            maxMountainY,
            oceanFloorY,
            seaLevel,
            EarthGenConfig.activeBelowSeaHeightMode(),
            EarthGenConfig.activeAboveSeaHeightMode()
        );
    }

    public static synchronized void syncEarthSettings(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        int seaLevel,
        TerrainHeightMode belowSeaHeightMode,
        TerrainHeightMode aboveSeaHeightMode
    ) {
        syncEarthProfile(zoom, maxMountainY, oceanFloorY, seaLevel, belowSeaHeightMode, aboveSeaHeightMode);
    }

    public static synchronized void syncEarthProfile(EarthGenerationProfile requestedProfile) {
        EarthGenConfig.setActiveTerrainProfile(
            requestedProfile.maxMountainY(),
            requestedProfile.oceanFloorY(),
            requestedProfile.seaLevel(),
            requestedProfile.belowSeaHeightMode(),
            requestedProfile.aboveSeaHeightMode()
        );
        EarthGenConfig.setActiveZoom(requestedProfile.zoom());
        reconcileRuntime(requestedProfile, EarthRuntimeServices.Overrides.none(), false);
    }

    public static synchronized void clearRuntimeCaches() {
        EarthRuntimeContext context = runtimeContext;
        if (context != null) {
            context.clearCaches();
        }
    }

    static TerrariumRuntimeConfig runtimeConfig() {
        TerrariumRuntimeConfig current = runtimeConfig;
        return current == null ? TerrariumRuntimeConfig.defaults() : current;
    }

    public static long runtimeGeneration() {
        return runtimeGeneration;
    }

    public static int biomeSamplingCacheEntries() {
        return requireContext().terrainRuntimeState().biomeLocalCacheEntries();
    }

    public static int samplingThreadLocalIdleSeconds() {
        return requireContext().terrainRuntimeState().threadLocalIdleSeconds();
    }

    static synchronized EarthRuntimeContext refreshRuntimeContextForTesting(EarthGenerationProfile profile) {
        EarthGenConfig.setActiveZoom(profile.zoom());
        EarthGenConfig.setActiveTerrainProfile(
            profile.maxMountainY(),
            profile.oceanFloorY(),
            profile.seaLevel(),
            profile.belowSeaHeightMode(),
            profile.aboveSeaHeightMode()
        );
        reconcileRuntime(profile, EarthRuntimeServices.Overrides.none(), true);
        return runtimeContext;
    }

    static synchronized void overrideServicesForTesting(
        TerrariumTileService tileService,
        TerrariumTileService recoveryTileService,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService
    ) {
        Map<Integer, TerrariumTileService> supplementalTerrainTileServices = recoveryTileService == null
            ? Map.of()
            : Map.of(recoveryTileService.zoom(), recoveryTileService);
        overrideSupplementalTerrainServicesForTesting(
            tileService,
            supplementalTerrainTileServices,
            ecoregionTileService,
            surfaceWaterTileService
        );
    }

    static synchronized void overrideSupplementalTerrainServicesForTesting(
        TerrariumTileService tileService,
        Map<Integer, TerrariumTileService> supplementalTerrainTileServices,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService
    ) {
        EarthGenerationProfile profile = profileFromActiveConfig();
        reconcileRuntime(
            profile,
            new EarthRuntimeServices.Overrides(tileService, supplementalTerrainTileServices, ecoregionTileService, surfaceWaterTileService),
            false
        );
    }

    static synchronized void resetForTesting() {
        shutdown();
        gameDir = null;
        runtimeConfig = TerrariumRuntimeConfig.defaults();
        EarthGenConfig.setActiveZoom(EarthGenConfig.DEFAULT_ZOOM);
        EarthGenConfig.resetActiveTerrainProfile();
        BadTerrainTileRegistry.resetForTesting();
    }

    public static synchronized void shutdown() {
        BadTerrainTileRegistry.shutdown();
        EarthRuntimeContext context = runtimeContext;
        runtimeContext = null;
        if (context != null) {
            context.clearCaches();
            context.services().closeAll();
        }
    }

    private static synchronized void reconcileRuntime(
        EarthGenerationProfile requestedProfile,
        EarthRuntimeServices.Overrides overrides,
        boolean forceRebuild
    ) {
        TerrariumRuntimeConfig currentRuntimeConfig = runtimeConfig();
        EarthRuntimeContext previousContext = runtimeContext;
        EarthRuntimeServices previousServices = previousContext != null ? previousContext.services() : EarthRuntimeServices.empty();
        EarthGenerationProfile previousProfile = previousContext != null ? previousContext.profile() : profileFromActiveConfig();
        boolean profileChanged = previousContext == null || !requestedProfile.equals(previousProfile);
        boolean zoomChanged = previousContext == null || previousProfile.zoom() != requestedProfile.zoom();
        boolean terrainBaseUrlChanged = previousContext == null
            || !previousProfile.terrainBaseUrl().equals(requestedProfile.terrainBaseUrl());
        boolean biomesBaseUrlChanged = previousContext == null
            || !previousProfile.biomesBaseUrl().equals(requestedProfile.biomesBaseUrl());
        boolean surfaceWaterBaseUrlChanged = previousContext == null
            || !previousProfile.surfaceWaterBaseUrl().equals(requestedProfile.surfaceWaterBaseUrl());

        if (
            !forceRebuild
                && !profileChanged
                && (overrides == null || !overrides.hasAny())
                && previousContext != null
                && (gameDir == null || hasInitializedServices(previousServices))
        ) {
            return;
        }

        EarthRuntimeServices nextServices = resolveServices(
            previousContext,
            previousServices,
            requestedProfile,
            currentRuntimeConfig,
            zoomChanged,
            terrainBaseUrlChanged,
            biomesBaseUrlChanged,
            surfaceWaterBaseUrlChanged,
            forceRebuild
        );
        if (overrides != null && overrides.hasAny()) {
            nextServices = nextServices.withOverrides(overrides);
        }

        installRuntimeContext(requestedProfile, nextServices);
        if (previousContext != null) {
            previousServices.closeReplacedBy(nextServices);
        }
    }

    private static EarthRuntimeServices resolveServices(
        EarthRuntimeContext previousContext,
        EarthRuntimeServices previousServices,
        EarthGenerationProfile requestedProfile,
        TerrariumRuntimeConfig runtimeConfig,
        boolean zoomChanged,
        boolean terrainBaseUrlChanged,
        boolean biomesBaseUrlChanged,
        boolean surfaceWaterBaseUrlChanged,
        boolean forceRebuild
    ) {
        Path currentGameDir = gameDir;
        if (currentGameDir == null) {
            return previousContext == null ? EarthRuntimeServices.empty() : previousServices;
        }
        if (previousContext == null || forceRebuild) {
            return EarthRuntimeServices.create(currentGameDir, requestedProfile, runtimeConfig);
        }
        if (!hasInitializedServices(previousServices)) {
            return EarthRuntimeServices.create(currentGameDir, requestedProfile, runtimeConfig);
        }
        if (terrainBaseUrlChanged || biomesBaseUrlChanged) {
            return EarthRuntimeServices.create(currentGameDir, requestedProfile, runtimeConfig);
        }
        if (zoomChanged || surfaceWaterBaseUrlChanged) {
            return previousServices.forZoom(currentGameDir, requestedProfile, runtimeConfig);
        }
        return previousServices;
    }

    private static <T> T requireService(T service, String label) {
        if (service == null) {
            throw new IllegalStateException(label + " tile service not initialized");
        }
        return service;
    }

    private static void installRuntimeContext(EarthGenerationProfile profile, EarthRuntimeServices services) {
        EarthRuntimeContext previous = runtimeContext;
        runtimeContext = new EarthRuntimeContext(
            profile,
            services,
            TerrainService.newRuntimeState(runtimeConfig())
        );
        runtimeGeneration++;
        if (previous != null) {
            previous.clearCaches();
        }
    }

    private static EarthGenerationProfile profileFromActiveConfig() {
        EarthGenerationProfile currentProfile = runtimeContext != null
            ? runtimeContext.profile()
            : EarthGenerationProfile.defaults();
        return currentProfile.withTerrainShape(
            EarthGenConfig.activeZoom(),
            EarthGenConfig.activeMaxMountainY(),
            EarthGenConfig.activeOceanFloorY(),
            EarthGenConfig.activeSeaLevel(),
            EarthGenConfig.activeBelowSeaHeightMode(),
            EarthGenConfig.activeAboveSeaHeightMode()
        );
    }

    private static EarthGenerationProfile syncEarthProfile(
        int zoom,
        int maxMountainY,
        int oceanFloorY,
        int seaLevel,
        TerrainHeightMode belowSeaHeightMode,
        TerrainHeightMode aboveSeaHeightMode
    ) {
        EarthGenerationProfile baseProfile = runtimeContext != null
            ? runtimeContext.profile()
            : EarthGenerationProfile.defaults();
        EarthGenerationProfile requestedProfile = baseProfile.withTerrainShape(
            zoom,
            maxMountainY,
            oceanFloorY,
            seaLevel,
            belowSeaHeightMode,
            aboveSeaHeightMode
        );
        syncEarthProfile(requestedProfile);
        return requestedProfile;
    }

    private static boolean hasInitializedServices(EarthRuntimeServices services) {
        return services.tileService() != null
            && services.ecoregionTileService() != null
            && services.surfaceWaterTileService() != null;
    }
}
