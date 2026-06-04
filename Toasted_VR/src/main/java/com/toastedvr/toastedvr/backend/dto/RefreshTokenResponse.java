package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;

public record RefreshTokenResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    String refreshToken
) {
}
