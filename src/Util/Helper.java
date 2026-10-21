package Util;

import java.util.List;

/**
 * Provides numerical and indexing utilities shared by the academic solver code.
 */
public final class Helper {

    /** General tolerance for primal values, bounds, and integrality tests. */
    public static final double EPS = 1.0e-6;

    /** Tolerance used to decide whether a reduced cost is negative. */
    public static final double RC_EPS = 1.0e-6;

    private Helper() {
    }

    public static boolean is_integral(double value) {
        return Math.abs(value - Math.rint(value)) <= EPS;
    }

    public static int floor(double value) {
        return (int) Math.floor(value + EPS);
    }

    public static int ceiling(double value) {
        return (int) Math.ceil(value - EPS);
    }

    /** Returns the distance of the fractional part from 0.5. */
    public static double distance_to_half(double value) {
        double fractional_part = value - Math.floor(value);
        return Math.abs(fractional_part - 0.5);
    }

    /** Computes a nonnegative relative optimality gap. */
    public static double relative_gap(double lower_bound, double upper_bound) {
        if (!Double.isFinite(upper_bound)) {
            return Double.POSITIVE_INFINITY;
        }
        double denominator = Math.max(1.0, Math.abs(upper_bound));
        return Math.max(0.0, (upper_bound - lower_bound) / denominator);
    }

    /** Builds an unambiguous duplicate-detection key for a node sequence. */
    public static String route_key(List<Integer> nodes) {
        StringBuilder builder = new StringBuilder();
        for (int p = 0; p < nodes.size(); p++) {
            if (p > 0) {
                builder.append('-');
            }
            builder.append(nodes.get(p));
        }
        return builder.toString();
    }
}
