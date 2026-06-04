package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import jakarta.validation.constraints.NotNull;

public record UpdateKnowledgeLevelRequest(
    @NotNull KnowledgeLevel knowledgeLevel
) {
}
