package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
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
    KnowledgeLevel knowledgeLevel,
    Instant createdAt
) {
}
