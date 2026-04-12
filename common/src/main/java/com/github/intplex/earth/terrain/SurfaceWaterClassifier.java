package com.github.intplex.earth.terrain;

public final class SurfaceWaterClassifier {
    static final int DRY_RGB = 0xFFFFFF;
    static final int NO_DATA_RGB = 0xCCCCCC;
    static final int WATER_LIGHT_RGB = 0x99D9EA;
    static final int WATER_DARK_RGB = 0x0000AA;

    private SurfaceWaterClassifier() {
    }

    public static int seasonalityMonths(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) {
            return 0;
        }

        int rgb = argb & 0x00FFFFFF;
        if (colorDistanceSquared(rgb, DRY_RGB) <= 32 * 32) {
            return 0;
        }
        if (colorDistanceSquared(rgb, NO_DATA_RGB) <= 28 * 28) {
            return 0;
        }

        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        if (blue < green || blue <= red) {
            return 0;
        }

        double waterDistance = distanceToWaterGradientSquared(rgb);
        double dryDistance = Math.min(colorDistanceSquared(rgb, DRY_RGB), colorDistanceSquared(rgb, NO_DATA_RGB));
        if (waterDistance > dryDistance) {
            return 0;
        }

        double projection = gradientProjection(rgb);
        double monthValue = 12.0 - projection * 11.0;
        return Math.max(1, Math.min(12, (int) Math.round(monthValue)));
    }

    public static boolean isWater(int argb, int minWaterMonths) {
        return seasonalityMonths(argb) >= Math.max(1, Math.min(12, minWaterMonths));
    }

    private static int colorDistanceSquared(int leftRgb, int rightRgb) {
        int leftRed = (leftRgb >>> 16) & 0xFF;
        int leftGreen = (leftRgb >>> 8) & 0xFF;
        int leftBlue = leftRgb & 0xFF;
        int rightRed = (rightRgb >>> 16) & 0xFF;
        int rightGreen = (rightRgb >>> 8) & 0xFF;
        int rightBlue = rightRgb & 0xFF;
        int dr = leftRed - rightRed;
        int dg = leftGreen - rightGreen;
        int db = leftBlue - rightBlue;
        return dr * dr + dg * dg + db * db;
    }

    private static double distanceToWaterGradientSquared(int rgb) {
        double projection = gradientProjection(rgb);
        double clampedProjection = Math.max(0.0, Math.min(1.0, projection));
        double startRed = (WATER_DARK_RGB >>> 16) & 0xFF;
        double startGreen = (WATER_DARK_RGB >>> 8) & 0xFF;
        double startBlue = WATER_DARK_RGB & 0xFF;
        double deltaRed = ((WATER_LIGHT_RGB >>> 16) & 0xFF) - startRed;
        double deltaGreen = ((WATER_LIGHT_RGB >>> 8) & 0xFF) - startGreen;
        double deltaBlue = (WATER_LIGHT_RGB & 0xFF) - startBlue;
        double nearestRed = startRed + deltaRed * clampedProjection;
        double nearestGreen = startGreen + deltaGreen * clampedProjection;
        double nearestBlue = startBlue + deltaBlue * clampedProjection;
        double diffRed = red(rgb) - nearestRed;
        double diffGreen = green(rgb) - nearestGreen;
        double diffBlue = blue(rgb) - nearestBlue;
        return diffRed * diffRed + diffGreen * diffGreen + diffBlue * diffBlue;
    }

    private static double gradientProjection(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;

        double startRed = (WATER_DARK_RGB >>> 16) & 0xFF;
        double startGreen = (WATER_DARK_RGB >>> 8) & 0xFF;
        double startBlue = WATER_DARK_RGB & 0xFF;
        double deltaRed = ((WATER_LIGHT_RGB >>> 16) & 0xFF) - startRed;
        double deltaGreen = ((WATER_LIGHT_RGB >>> 8) & 0xFF) - startGreen;
        double deltaBlue = (WATER_LIGHT_RGB & 0xFF) - startBlue;
        double lengthSquared = deltaRed * deltaRed + deltaGreen * deltaGreen + deltaBlue * deltaBlue;
        return lengthSquared <= 0.0
            ? 0.0
            : ((red - startRed) * deltaRed + (green - startGreen) * deltaGreen + (blue - startBlue) * deltaBlue) / lengthSquared;
    }

    private static int red(int rgb) {
        return (rgb >>> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >>> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
