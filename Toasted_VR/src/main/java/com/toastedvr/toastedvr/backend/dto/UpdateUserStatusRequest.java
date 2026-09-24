package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull(message = "{admin.user.status.enabledRequired}")
    Boolean enabled
) {
}
