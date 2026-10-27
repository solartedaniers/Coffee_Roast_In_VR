package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CurrentPasswordRequest(
    @NotBlank(message = "The current password is required.")
    String currentPassword
) {
}
