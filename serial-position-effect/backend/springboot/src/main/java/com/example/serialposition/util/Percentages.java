package com.example.serialposition.util;

public final class Percentages {

    private Percentages() {
    }

    /** {@code part / total} as a percentage rounded to two decimals; 0 when total is 0. */
    public static double of(int part, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(part * 10000.0 / total) / 100.0;
    }
}
