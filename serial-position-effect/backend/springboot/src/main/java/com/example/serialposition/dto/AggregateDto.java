package com.example.serialposition.dto;

import java.util.List;

/** Recall percentage per serial position across all participants, split by test type. */
public record AggregateDto(
        int participants,
        List<PositionAggregateDto> normal,
        List<PositionAggregateDto> semantic
) {
}
