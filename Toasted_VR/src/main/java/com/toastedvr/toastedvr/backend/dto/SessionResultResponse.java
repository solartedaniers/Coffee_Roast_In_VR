package com.toastedvr.toastedvr.backend.dto;

import java.time.LocalDateTime;

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
    LocalDateTime createdAt
) {
}
