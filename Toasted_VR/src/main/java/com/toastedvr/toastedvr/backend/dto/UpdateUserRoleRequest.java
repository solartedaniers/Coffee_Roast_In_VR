package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
    @NotNull(message = "El rol es obligatorio")
    Role role
) {
}
