package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.time.Instant;

// Respuesta al guardar una sesión y detalle en el historial (RF016).
public record SessionResultResponse(
    Long id,
    String result,
    Integer qualityScore,
    Double chargeTemperature,
    Double targetTemperature,
    Integer totalDurationSeconds,
    Double finalTemperature,
    Double peakTemperature,
    Boolean firstCrackReached,
    Integer developmentTimeSeconds,
    Double developmentTimeRatio,
    KnowledgeLevel knowledgeLevel,
    Instant createdAt
) {
}
