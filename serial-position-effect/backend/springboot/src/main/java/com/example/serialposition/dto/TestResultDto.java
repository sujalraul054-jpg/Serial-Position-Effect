package com.example.serialposition.dto;

import com.example.serialposition.model.TestType;

import java.util.List;

/** Scored outcome of one test. */
public record TestResultDto(
        TestType testType,
        int totalWords,
        int totalRecalled,
        int middleRecalled,
        double recallPercentage,
        double primacyScore,
        double middleScore,
        double recencyScore,
        List<PositionResultDto> positionResults,
        List<String> duplicateWords
) {
}
