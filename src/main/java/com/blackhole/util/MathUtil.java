package com.blackhole.util;

import java.util.List;

public final class MathUtil {

    private MathUtil() {
    }

    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    /**
     * GCD across a whole sample window, folding in a tolerance so float noise
     * doesn't collapse every window to 1 (used by AimbotCheck on yaw/pitch deltas).
     */
    public static long gcd(List<Long> values) {
        long result = 0;
        for (long value : values) {
            result = gcd(result, value);
        }
        return result;
    }

    public static double mean(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    public static double variance(List<Long> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double sumSquaredDiff = 0.0;
        for (long v : values) {
            double diff = v - mean;
            sumSquaredDiff += diff * diff;
        }
        return sumSquaredDiff / values.size();
    }

    /** Normalizes a rotation delta to (-180, 180], the shortest signed turn. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
