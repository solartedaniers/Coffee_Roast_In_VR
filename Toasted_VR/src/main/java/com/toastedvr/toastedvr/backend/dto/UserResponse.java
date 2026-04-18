package com.toastedvr.toastedvr.backend.dto;

public record UserResponse(
    Long id,
    String name,
    String email,
    String username,
    boolean emailVerified,
    String message
) {
}
