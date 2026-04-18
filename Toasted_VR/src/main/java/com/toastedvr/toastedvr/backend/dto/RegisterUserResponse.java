package com.toastedvr.toastedvr.backend.dto;

public record RegisterUserResponse(
    String message,
    String email,
    int expiresInMinutes
) {
}
