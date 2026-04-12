package com.github.intplex.earth.terrain;

final class EarthRuntimeContext {
    private final EarthGenerationProfile profile;
    private final EarthRuntimeServices services;
    private final TerrainService.RuntimeState terrainRuntimeState;

    EarthRuntimeContext(
        EarthGenerationProfile profile,
        EarthRuntimeServices services,
        TerrainService.RuntimeState terrainRuntimeState
    ) {
        this.profile = profile;
        this.services = services;
        this.terrainRuntimeState = terrainRuntimeState;
    }

    EarthGenerationProfile profile() {
        return profile;
    }

    EarthRuntimeServices services() {
        return services;
    }

    TerrainService.RuntimeState terrainRuntimeState() {
        return terrainRuntimeState;
    }

    void clearCaches() {
        terrainRuntimeState.clear();
    }
}
