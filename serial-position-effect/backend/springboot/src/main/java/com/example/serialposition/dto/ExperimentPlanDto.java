package com.example.serialposition.dto;

import java.util.List;

/** The two tests of a session, in the order they are to be presented. */
public record ExperimentPlanDto(List<TestPlanDto> tests) {
}
