package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record UnityAccessCodeResponse(
    String code,
    LocalDateTime createdAt,
    LocalDateTime regeneratedAt,
    Instant expiresAt
) {
}
