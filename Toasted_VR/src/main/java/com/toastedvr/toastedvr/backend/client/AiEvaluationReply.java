package com.toastedvr.toastedvr.backend.client;

import java.util.List;

// Respuesta de POST /api/v1/evaluation del servicio IA. status es COMPLETED o NOT_APPLICABLE; score es
// el porcentaje de la rúbrica (null si ningún criterio aplica).
public record AiEvaluationReply(
    String status,
    String rubricVersion,
    String model,
    Double score,
    List<Criterion> criteria,
    List<String> notApplicable
) {

    public static final String COMPLETED = "COMPLETED";

    public boolean completed() {
        return COMPLETED.equals(status) && score != null;
    }

    public record Criterion(String id, Integer level, Integer maxLevel, String justification) {
    }
}
