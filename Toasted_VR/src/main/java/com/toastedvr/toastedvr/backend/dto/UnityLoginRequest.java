package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UnityLoginRequest(
    @NotBlank(message = "The access code is required.")
    String code
) {
}
