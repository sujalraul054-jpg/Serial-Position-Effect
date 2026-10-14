package com.example.serialposition.dto;

import com.example.serialposition.model.TestType;

import java.util.List;

public record TestPlanDto(TestType testType, List<String> words) {
}
