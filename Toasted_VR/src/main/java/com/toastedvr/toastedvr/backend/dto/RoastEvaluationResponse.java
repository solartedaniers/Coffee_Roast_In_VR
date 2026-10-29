package com.toastedvr.toastedvr.backend.dto;

import java.util.List;

// Calificación combinada de una sesión (fase 5). finalScore es null mientras status es PENDING o si
// quedó FAILED; rubricScore y los pesos solo existen si la IA calificó (COMPLETED).
public record RoastEvaluationResponse(
    String status,
    String skipReason,
    Integer finalScore,
    Integer rulesScore,
    Double rubricScore,
    Double rulesWeight,
    Double aiWeight,
    Boolean defectCapApplied,
    String rubricVersion,
    String model,
    List<Criterion> criteria,
    List<String> notApplicable
) {

    public record Criterion(String id, Integer level, Integer maxLevel, String justification) {
    }
}
