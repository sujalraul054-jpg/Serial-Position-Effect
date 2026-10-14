package com.example.serialposition.model;

public enum TestType {
    NORMAL("Normal"),
    SEMANTIC("Semantic");

    private final String label;

    TestType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
