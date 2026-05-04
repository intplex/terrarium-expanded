package com.github.intplex.earth.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthSurfaceCavesDensityFunctionTest {
    @Test
    void aboveEarthSurfaceIsAlwaysAir() {
        assertEquals(
            -1.0,
            EarthSurfaceCavesDensityFunction.computeDensity(81, 80, false, 1.0, 1.0),
            1.0e-12
        );
        assertEquals(
            -1.0,
            EarthSurfaceCavesDensityFunction.computeDensity(81, 80, true, 1.0, 1.0),
            1.0e-12
        );
    }

    @Test
    void cavesDisabledIsSolidBelowEarthSurface() {
        assertEquals(
            1.0,
            EarthSurfaceCavesDensityFunction.computeDensity(40, 80, false, 1.0, -0.5),
            1.0e-12
        );
    }

    @Test
    void cavesEnabledPreservesVanillaCaveAirOnlyUnderground() {
        assertEquals(
            -0.5,
            EarthSurfaceCavesDensityFunction.computeDensity(40, 80, true, 0.25, -0.5),
            1.0e-12
        );
        assertEquals(
            1.0,
            EarthSurfaceCavesDensityFunction.computeDensity(40, 80, true, -0.25, -0.5),
            1.0e-12
        );
    }

    @Test
    void vanillaSkyUnderHighEarthTerrainRemainsSolid() {
        assertEquals(
            1.0,
            EarthSurfaceCavesDensityFunction.computeDensity(160, 220, true, -0.25, -0.5),
            1.0e-12
        );
    }
}
