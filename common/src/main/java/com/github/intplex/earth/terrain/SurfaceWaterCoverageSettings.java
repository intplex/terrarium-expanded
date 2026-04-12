package com.github.intplex.earth.terrain;

public record SurfaceWaterCoverageSettings(double minLatitude, double maxLatitude) {
    public static final double DEFAULT_MIN_LATITUDE = -60.0;
    public static final double DEFAULT_MAX_LATITUDE = 77.0;
    public static final SurfaceWaterCoverageSettings DEFAULT =
        new SurfaceWaterCoverageSettings(DEFAULT_MIN_LATITUDE, DEFAULT_MAX_LATITUDE);

    public boolean intersectsTile(TileKey key, int zoom) {
        double northLatitude = tileRowNorthLatitude(key.y(), zoom);
        double southLatitude = tileRowNorthLatitude(key.y() + 1, zoom);
        return !(northLatitude < minLatitude || southLatitude > maxLatitude);
    }

    public static double tileRowNorthLatitude(int tileY, int zoom) {
        double tilesPerAxis = Math.scalb(1.0, zoom);
        double normalizedY = tileY / tilesPerAxis;
        double mercatorN = Math.PI * (1.0 - (2.0 * normalizedY));
        return Math.toDegrees(Math.atan(Math.sinh(mercatorN)));
    }
}
