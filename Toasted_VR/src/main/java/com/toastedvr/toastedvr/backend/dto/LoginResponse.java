package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    AuthenticatedUserResponse user
) {
}
