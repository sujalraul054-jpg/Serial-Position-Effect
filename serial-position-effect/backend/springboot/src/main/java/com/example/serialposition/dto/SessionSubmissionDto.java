package com.example.serialposition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Both tests of a session, in presentation order. */
public record SessionSubmissionDto(
        @NotNull(message = "tests is required")
        @Size(min = 2, max = 2, message = "a session must contain exactly 2 tests")
        @Valid
        List<TestSubmissionDto> tests
) {
}
