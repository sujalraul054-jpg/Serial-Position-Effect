package com.example.serialposition.dto;

import com.example.serialposition.model.TestType;

import java.util.List;

/**
 * Tests are listed in the order they were presented. {@code recallImprovement} is
 * ((semantic recall - normal recall) / normal recall) * 100, and is null when
 * normal recall is 0.
 */
public record SessionResultDto(
        String participantId,
        TestType firstTestType,
        List<TestResultDto> tests,
        Double recallImprovement,
        Double middleRecallImprovement
) {
}
