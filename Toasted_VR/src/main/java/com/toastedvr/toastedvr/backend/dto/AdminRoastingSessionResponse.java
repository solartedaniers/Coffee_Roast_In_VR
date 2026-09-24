package com.toastedvr.toastedvr.backend.dto;

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
    Instant createdAt
) {
}
