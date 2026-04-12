package com.github.intplex.earth.terrain;

import com.github.intplex.earth.EarthGenConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;

public final class OceanSurfaceTemperatureService {
    static final String RESOURCE_PATH = "/data/terrarium_expanded/ocean/woa23_sst_1deg_surface_annual_1991_2020.i16le";
    static final int LAT_CELLS = 180;
    static final int LON_CELLS = 360;
    static final int CELL_COUNT = LAT_CELLS * LON_CELLS;
    static final double SCALE_C = 0.01;
    static final double LAT_START_CENTER = -89.5;
    static final double LON_START_CENTER = -179.5;

    private static volatile short[] packedSst;

    private OceanSurfaceTemperatureService() {
    }

    public static OptionalDouble sampleMeanAnnualSstAtBlock(int blockX, int blockZ, int zoom) {
        var geo = EarthGenConfig.blockToGeo(blockX, blockZ, zoom);
        if (geo.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sampleMeanAnnualSst(geo.get().latitude(), geo.get().longitude()));
    }

    static double sampleMeanAnnualSst(double latitude, double longitude) {
        short[] grid = requirePackedSst();

        double latIndex = clamp(latitude, LAT_START_CENTER, LAT_START_CENTER + (LAT_CELLS - 1)) - LAT_START_CENTER;
        double lonIndex = positiveMod(longitude - LON_START_CENTER, LON_CELLS);

        int lat0 = (int) Math.floor(latIndex);
        int lat1 = Math.min(lat0 + 1, LAT_CELLS - 1);
        int lon0 = (int) Math.floor(lonIndex);
        int lon1 = (lon0 + 1) % LON_CELLS;

        double latT = latIndex - lat0;
        double lonT = lonIndex - lon0;

        double q00 = decode(grid, lat0, lon0);
        double q01 = decode(grid, lat0, lon1);
        double q10 = decode(grid, lat1, lon0);
        double q11 = decode(grid, lat1, lon1);

        double top = lerp(q00, q01, lonT);
        double bottom = lerp(q10, q11, lonT);
        return lerp(top, bottom, latT);
    }

    static short[] requirePackedSst() {
        short[] local = packedSst;
        if (local != null) {
            return local;
        }
        synchronized (OceanSurfaceTemperatureService.class) {
            local = packedSst;
            if (local == null) {
                local = loadPackedSst();
                packedSst = local;
            }
            return local;
        }
    }

    public static void setPackedSstForTesting(short[] replacement) {
        if (replacement != null && replacement.length != CELL_COUNT) {
            throw new IllegalArgumentException("Unexpected SST grid length " + replacement.length + "; expected " + CELL_COUNT);
        }
        packedSst = replacement;
    }

    private static short[] loadPackedSst() {
        try (InputStream stream = OceanSurfaceTemperatureService.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing ocean SST resource: " + RESOURCE_PATH);
            }
            byte[] bytes = stream.readAllBytes();
            int expectedBytes = CELL_COUNT * Short.BYTES;
            if (bytes.length != expectedBytes) {
                throw new IllegalStateException(
                    "Unexpected ocean SST resource size " + bytes.length + "; expected " + expectedBytes
                );
            }
            short[] out = new short[CELL_COUNT];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out);
            return out;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed reading ocean SST resource: " + RESOURCE_PATH, exception);
        }
    }

    private static double decode(short[] grid, int latIndex, int lonIndex) {
        return grid[latIndex * LON_CELLS + lonIndex] * SCALE_C;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double positiveMod(double value, double modulus) {
        double raw = value % modulus;
        return raw < 0.0 ? raw + modulus : raw;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
