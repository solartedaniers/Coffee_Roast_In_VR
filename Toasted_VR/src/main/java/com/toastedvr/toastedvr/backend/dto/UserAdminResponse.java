package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.Role;
import java.time.LocalDateTime;

public record UserAdminResponse(
    Long id,
    String name,
    String email,
    String username,
    boolean emailVerified,
    boolean enabled,
    Role role,
    LocalDateTime createdAt,
    String message
) {
}
