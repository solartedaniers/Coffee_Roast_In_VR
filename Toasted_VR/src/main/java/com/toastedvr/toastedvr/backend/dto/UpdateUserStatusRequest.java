package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull(message = "El campo enabled es obligatorio")
    Boolean enabled
) {
}
