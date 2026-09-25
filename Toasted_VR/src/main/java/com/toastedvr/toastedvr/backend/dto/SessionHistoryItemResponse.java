package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.time.Instant;

// Una fila del historial del jugador (RF016). knowledgeLevel es null en las
// sesiones guardadas antes de RF009; developmentTimeRatio es null si no hubo
// first crack.
public record SessionHistoryItemResponse(
    Long id,
    Instant createdAt,
    KnowledgeLevel knowledgeLevel,
    String result,
    Integer qualityScore,
    Double finalTemperature,
    Integer totalDurationSeconds,
    Double developmentTimeRatio
) {
}
