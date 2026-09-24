package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import java.time.Instant;

public record UserSummaryResponse(
    Long id,
    String name,
    String email,
    String username,
    boolean emailVerified,
    boolean enabled,
    Role role,
    Instant createdAt,
    KnowledgeLevel knowledgeLevel
) {
}
