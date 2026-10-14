package com.example.serialposition.util;

public final class RecallImprovement {

    private RecallImprovement() {
    }

    /**
     * ((semantic - normal) / normal) * 100, rounded to two decimals.
     * Returns null when normal recall is 0, where the ratio is undefined.
     */
    public static Double of(int normalRecalled, int semanticRecalled) {
        return ofPercentages(normalRecalled, semanticRecalled);
    }

    /** ((semantic percentage - normal percentage) / normal percentage) * 100. */
    public static Double ofPercentages(double normalRecall, double semanticRecall) {
        if (normalRecall == 0.0) {
            return null;
        }
        return Math.round((semanticRecall - normalRecall) * 100.0 / normalRecall * 100.0) / 100.0;
    }
}
