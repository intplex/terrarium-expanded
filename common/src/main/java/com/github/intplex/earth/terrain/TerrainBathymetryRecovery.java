package com.github.intplex.earth.terrain;

import java.util.OptionalDouble;

public final class TerrainBathymetryRecovery {
    private TerrainBathymetryRecovery() {
    }

    public static double applyIfEligible(
        int blockX,
        int blockZ,
        int worldZoom,
        boolean terrainSampleAvailable,
        double meters,
        EcoregionProbe ecoregionProbe,
        SurfaceWaterProbe surfaceWaterProbe,
        OceanBathymetryRecovery.RecoverySampleCache recoverySampleCache,
        OceanBathymetryRecovery.SourceZoomMetersSampler sampler
    ) {
        if (!OceanBathymetryRecovery.shouldAttemptRecovery(worldZoom, terrainSampleAvailable, meters)) {
            return meters;
        }

        OceanBathymetryRecovery.recordRecoveryAttempted();
        EcoregionGate ecoregionGate = ecoregionProbe.readNoDataGate();
        if (ecoregionGate == EcoregionGate.UNAVAILABLE) {
            OceanBathymetryRecovery.recordGateFailedTile();
            return meters;
        }
        if (ecoregionGate == EcoregionGate.NOT_NO_DATA) {
            OceanBathymetryRecovery.recordGateFailedEcoregion();
            return meters;
        }
        if (!surfaceWaterProbe.isWater()) {
            OceanBathymetryRecovery.recordGateFailedWater();
            return meters;
        }

        OptionalDouble recoveredMeters = OceanBathymetryRecovery.sampleRecoveryChainMeters(
            blockX,
            blockZ,
            worldZoom,
            sampler,
            recoverySampleCache
        );
        if (recoveredMeters.isEmpty()) {
            OceanBathymetryRecovery.recordGateFailedTile();
            return meters;
        }

        OceanBathymetryRecovery.recordRecoveryApplied();
        return recoveredMeters.getAsDouble();
    }

    public enum EcoregionGate {
        NO_DATA,
        NOT_NO_DATA,
        UNAVAILABLE
    }

    @FunctionalInterface
    public interface EcoregionProbe {
        EcoregionGate readNoDataGate();
    }

    @FunctionalInterface
    public interface SurfaceWaterProbe {
        boolean isWater();
    }
}
