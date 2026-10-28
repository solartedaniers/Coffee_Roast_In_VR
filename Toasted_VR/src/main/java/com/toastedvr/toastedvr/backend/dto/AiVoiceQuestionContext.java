package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.util.UUID;

// Contexto que el backend le reenvía al servicio IA, con el usuario y el nivel
// tomados del servidor para que Unity no los pueda falsificar.
public record AiVoiceQuestionContext(
    Long userId,
    KnowledgeLevel knowledgeLevel,
    UUID roastAttemptId,
    RoastStateRequest roastState
) {

    public static AiVoiceQuestionContext from(Long userId, KnowledgeLevel knowledgeLevel, VoiceQuestionRequest request) {
        return new AiVoiceQuestionContext(userId, knowledgeLevel, request.roastAttemptId(), request.roastState());
    }
}
