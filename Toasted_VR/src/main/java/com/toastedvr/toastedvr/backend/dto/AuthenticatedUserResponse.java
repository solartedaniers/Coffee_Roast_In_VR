package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import java.time.LocalDateTime;

public record AuthenticatedUserResponse(
    Long id,
    String name,
    String email,
    String username,
    String profileImageUrl,
    boolean emailVerified,
    boolean enabled,
    Role role,
    LocalDateTime lastLoginAt,
    KnowledgeLevel knowledgeLevel
) {
    public static AuthenticatedUserResponse from(User user) {
        return new AuthenticatedUserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getUsername(),
            user.getProfileImageUrl(),
            user.isEmailVerified(),
            user.isEnabled(),
            user.getRole(),
            user.getLastLoginAt(),
            user.getKnowledgeLevel()
        );
    }
}
