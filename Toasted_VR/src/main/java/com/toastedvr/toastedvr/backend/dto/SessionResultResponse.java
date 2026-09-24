package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;

public record SessionResultResponse(
    Long id,
    String result,
    Integer qualityScore,
    Double targetTemperature,
    Integer totalDurationSeconds,
    Double finalTemperature,
    Double peakTemperature,
    Boolean firstCrackReached,
    Integer developmentTimeSeconds,
    Instant createdAt
) {
}
