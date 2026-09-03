package com.toastedvr.toastedvr.backend.dto;

import java.time.LocalDateTime;

public record UnityAccessCodeStatusResponse(
    boolean exists,
    LocalDateTime createdAt,
    LocalDateTime regeneratedAt,
    LocalDateTime lastEmailedAt
) {
}
