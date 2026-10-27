package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record UnityAccessCodeStatusResponse(
    boolean exists,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime regeneratedAt,
    LocalDateTime lastEmailedAt,
    Instant expiresAt
) {
}
