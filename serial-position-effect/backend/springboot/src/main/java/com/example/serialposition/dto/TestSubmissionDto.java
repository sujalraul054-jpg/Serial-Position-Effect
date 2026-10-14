package com.example.serialposition.dto;

import com.example.serialposition.model.TestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TestSubmissionDto(
        @NotNull(message = "testType is required")
        TestType testType,

        @NotNull(message = "words is required")
        @Size(min = 10, max = 10, message = "words must contain exactly 10 entries")
        List<@NotBlank String> words,

        @NotEmpty(message = "recalledWords must not be empty")
        @Size(max = 200, message = "recalledWords must not exceed 200 entries")
        List<@NotBlank @Size(max = 40, message = "each recalled word must be at most 40 characters") String> recalledWords
) {
}
