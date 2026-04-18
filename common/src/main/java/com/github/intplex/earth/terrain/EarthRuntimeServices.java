package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.nio.file.Path;
import java.util.Objects;

final class EarthRuntimeServices {
    private static final EarthRuntimeServices EMPTY = new EarthRuntimeServices(null, null, null, null);

    private final TerrariumTileService tileService;
    private final TerrariumTileService recoveryTileService;
    private final EcoregionTileService ecoregionTileService;
    private final SurfaceWaterTileService surfaceWaterTileService;

    EarthRuntimeServices(
        TerrariumTileService tileService,
        TerrariumTileService recoveryTileService,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService
    ) {
        this.tileService = tileService;
        this.recoveryTileService = recoveryTileService;
        this.ecoregionTileService = ecoregionTileService;
        this.surfaceWaterTileService = surfaceWaterTileService;
    }

    static EarthRuntimeServices empty() {
        return EMPTY;
    }

    static EarthRuntimeServices create(Path gameDir, EarthGenerationProfile profile, TerrariumRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(gameDir, "gameDir");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        return new EarthRuntimeServices(
            TerrariumTileService.create(
                gameDir,
                profile.zoom(),
                profile.terrainBaseUrl(),
                runtimeConfig.terrainTiles(),
                runtimeConfig.ioThreadsPerService()
            ),
            TerrariumTileService.create(
                gameDir,
                OceanBathymetryRecovery.SOURCE_ZOOM,
                profile.terrainBaseUrl(),
                runtimeConfig.recoveryTiles(),
                runtimeConfig.ioThreadsPerService()
            ),
            EcoregionTileService.create(
                gameDir,
                profile.biomesBaseUrl(),
                runtimeConfig.ecoregionTiles(),
                runtimeConfig.ioThreadsPerService()
            ),
            SurfaceWaterTileService.create(
                gameDir,
                EarthGenConfig.waterSourceZoomForWorldZoom(profile.zoom()),
                profile.surfaceWaterBaseUrl(),
                runtimeConfig.surfaceWaterTiles(),
                runtimeConfig.ioThreadsPerService()
            )
        );
    }

    EarthRuntimeServices forZoom(Path gameDir, EarthGenerationProfile profile, TerrariumRuntimeConfig runtimeConfig) {
        Objects.requireNonNull(gameDir, "gameDir");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        return new EarthRuntimeServices(
            TerrariumTileService.create(
                gameDir,
                profile.zoom(),
                profile.terrainBaseUrl(),
                runtimeConfig.terrainTiles(),
                runtimeConfig.ioThreadsPerService()
            ),
            recoveryTileService,
            ecoregionTileService,
            SurfaceWaterTileService.create(
                gameDir,
                EarthGenConfig.waterSourceZoomForWorldZoom(profile.zoom()),
                profile.surfaceWaterBaseUrl(),
                runtimeConfig.surfaceWaterTiles(),
                runtimeConfig.ioThreadsPerService()
            )
        );
    }

    EarthRuntimeServices withOverrides(Overrides overrides) {
        if (overrides == null || !overrides.hasAny()) {
            return this;
        }
        return new EarthRuntimeServices(
            overrides.tileService() != null ? overrides.tileService() : tileService,
            overrides.recoveryTileService() != null ? overrides.recoveryTileService() : recoveryTileService,
            overrides.ecoregionTileService() != null ? overrides.ecoregionTileService() : ecoregionTileService,
            overrides.surfaceWaterTileService() != null ? overrides.surfaceWaterTileService() : surfaceWaterTileService
        );
    }

    TerrariumTileService tileService() {
        return tileService;
    }

    TerrariumTileService recoveryTileService() {
        return recoveryTileService;
    }

    EcoregionTileService ecoregionTileService() {
        return ecoregionTileService;
    }

    SurfaceWaterTileService surfaceWaterTileService() {
        return surfaceWaterTileService;
    }

    void closeAll() {
        close(tileService);
        close(recoveryTileService);
        close(ecoregionTileService);
        close(surfaceWaterTileService);
    }

    void closeReplacedBy(EarthRuntimeServices replacement) {
        if (replacement == null) {
            closeAll();
            return;
        }
        closeIfReplaced(tileService, replacement.tileService);
        closeIfReplaced(recoveryTileService, replacement.recoveryTileService);
        closeIfReplaced(ecoregionTileService, replacement.ecoregionTileService);
        closeIfReplaced(surfaceWaterTileService, replacement.surfaceWaterTileService);
    }

    private static void closeIfReplaced(AutoCloseable current, AutoCloseable replacement) {
        if (current == null || current == replacement) {
            return;
        }
        close(current);
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Service close failures should not block runtime context transitions.
        }
    }

    record Overrides(
        TerrariumTileService tileService,
        TerrariumTileService recoveryTileService,
        EcoregionTileService ecoregionTileService,
        SurfaceWaterTileService surfaceWaterTileService
    ) {
        private static final Overrides NONE = new Overrides(null, null, null, null);

        static Overrides none() {
            return NONE;
        }

        boolean hasAny() {
            return tileService != null
                || recoveryTileService != null
                || ecoregionTileService != null
                || surfaceWaterTileService != null;
        }
    }
}
