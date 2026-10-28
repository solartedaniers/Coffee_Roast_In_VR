package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// Contexto que Unity manda junto al audio. No trae usuario ni nivel: los pone el servidor.
public record VoiceQuestionRequest(
    @NotNull UUID roastAttemptId,
    @NotNull @Valid RoastStateRequest roastState
) {
}
