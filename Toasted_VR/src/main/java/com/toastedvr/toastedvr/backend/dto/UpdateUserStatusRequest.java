package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull(message = "The enabled field is required.")
    Boolean enabled
) {
}
