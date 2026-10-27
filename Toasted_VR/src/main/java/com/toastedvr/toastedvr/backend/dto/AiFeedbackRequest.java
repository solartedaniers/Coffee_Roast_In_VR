package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;

// Datos de la sesión que necesita el servicio IA para armar el prompt y la
// búsqueda en el RAG. No incluye nada del usuario más allá de su nivel.
public record AiFeedbackRequest(
    RoastingResult result,
    Integer qualityScore,
    Double chargeTemperature,
    Double targetTemperature,
    Double finalTemperature,
    Integer totalDurationSeconds,
    Boolean firstCrackReached,
    Integer developmentTimeSeconds,
    KnowledgeLevel knowledgeLevel
) {

    public static AiFeedbackRequest from(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        return new AiFeedbackRequest(
            session.getResult(),
            session.getQualityScore(),
            session.getChargeTemperature(),
            session.getTargetTemperature(),
            session.getFinalTemperature(),
            session.getTotalDurationSeconds(),
            session.isFirstCrackReached(),
            session.getDevelopmentTimeSeconds(),
            knowledgeLevel
        );
    }
}
