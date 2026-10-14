package com.example.serialposition.util;

public final class RecallImprovement {

    private RecallImprovement() {
    }

    /**
     * ((semantic - normal) / normal) * 100, rounded to two decimals.
     * Returns null when normal recall is 0, where the ratio is undefined.
     */
    public static Double of(int normalRecalled, int semanticRecalled) {
        if (normalRecalled == 0) {
            return null;
        }
        return Math.round((semanticRecalled - normalRecalled) * 10000.0 / normalRecalled) / 100.0;
    }
}
