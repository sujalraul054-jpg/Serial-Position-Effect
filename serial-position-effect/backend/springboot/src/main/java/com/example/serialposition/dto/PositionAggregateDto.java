package com.example.serialposition.dto;

public record PositionAggregateDto(int position, int timesPresented, int timesRecalled, double recallPercentage) {
}
