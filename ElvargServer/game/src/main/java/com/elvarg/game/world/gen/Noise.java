package com.elvarg.game.world.gen;

/**
 * Deterministic value noise with fractal (fBm) and domain-warping helpers.
 *
 * Everything here is a pure function of the world seed and the coordinates asked
 * for. There is no internal state and no reliance on {@link java.util.Random},
 * whose stream order would make results depend on the order the generator happens
 * to query things - which would break the promise that a seed reproduces a world.
 *
 * @author EverGielinor world generator
 */
public final class Noise {

    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    /**
     * A derived generator for an independent channel (elevation, moisture, ...) of
     * the same world, so channels never correlate.
     */
    public Noise channel(int channel) {
        return new Noise(mix(seed, channel * 0x9E3779B97F4A7C15L));
    }

    /** Hash of an integer lattice point, as a uniform double in [0, 1). */
    private double lattice(int x, int y) {
        long h = mix(seed, ((long) x << 32) ^ (y & 0xffffffffL));
        // Use the high 53 bits, which are the well-mixed ones.
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long a, long b) {
        long z = a ^ (b + 0x9E3779B97F4A7C15L + (a << 6) + (a >>> 2));
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Smoothstep, so interpolated noise has continuous first derivatives. */
    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Bilinearly interpolated value noise at an arbitrary point. */
    public double value(double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double v00 = lattice(x0, y0);
        double v10 = lattice(x0 + 1, y0);
        double v01 = lattice(x0, y0 + 1);
        double v11 = lattice(x0 + 1, y0 + 1);
        double top = v00 + (v10 - v00) * fx;
        double bottom = v01 + (v11 - v01) * fx;
        return top + (bottom - top) * fy;
    }

    /**
     * Fractal Brownian motion: octaves of value noise at doubling frequency and
     * halving amplitude. Returns a value in [0, 1].
     *
     * @param scale tiles per unit cell of the lowest-frequency octave
     */
    public double fbm(double x, double y, double scale, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1.0 / scale;
        for (int i = 0; i < octaves; i++) {
            sum += value(x * frequency, y * frequency) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    /**
     * Ridged noise, which produces the creased lines that read as mountain ridges
     * rather than the rounded blobs plain fBm gives.
     */
    public double ridged(double x, double y, double scale, int octaves) {
        double sum = 0;
        double amplitude = 1;
        double total = 0;
        double frequency = 1.0 / scale;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(value(x * frequency, y * frequency) * 2.0 - 1.0);
            sum += n * n * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    /**
     * Offsets a lookup by another noise field, which turns straight or circular
     * features into meandering natural ones. Used to keep the coastline from
     * reading as an obvious circle.
     */
    public double warpedFbm(double x, double y, double scale, int octaves, double warpStrength) {
        Noise wx = channel(101);
        Noise wy = channel(102);
        double ox = (wx.fbm(x, y, scale * 2, 3) - 0.5) * warpStrength;
        double oy = (wy.fbm(x, y, scale * 2, 3) - 0.5) * warpStrength;
        return fbm(x + ox, y + oy, scale, octaves);
    }

    /** A stable pseudo-random double in [0,1) for a discrete key, order-independent. */
    public double at(int a, int b) {
        return lattice(a, b);
    }

    /** A stable pseudo-random int in [0, bound) for a discrete key. */
    public int intAt(int a, int b, int bound) {
        return (int) (lattice(a, b) * bound);
    }
}
