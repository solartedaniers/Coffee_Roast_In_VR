package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.time.Instant;

// Respuesta al guardar una sesión y detalle en el historial (RF016). finalScore es la nota que vale
// (la combinada si existe, si no qualityScore) y grade su calificación por rango; evaluation es null
// en las sesiones guardadas antes de la calificación combinada.
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
    Instant createdAt,
    Integer finalScore,
    String grade,
    RoastEvaluationResponse evaluation
) {
}
