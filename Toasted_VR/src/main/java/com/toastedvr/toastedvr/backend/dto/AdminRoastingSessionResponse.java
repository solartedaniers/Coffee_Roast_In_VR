package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.time.Instant;

public record AdminRoastingSessionResponse(
    Long id,
    String userName,
    String userEmail,
    String userUsername,
    Double targetTemperature,
    Integer totalDurationSeconds,
    Double finalTemperature,
    String result,
    Integer qualityScore,
    Boolean firstCrackReached,
    KnowledgeLevel knowledgeLevel,
    Instant createdAt
) {
}
