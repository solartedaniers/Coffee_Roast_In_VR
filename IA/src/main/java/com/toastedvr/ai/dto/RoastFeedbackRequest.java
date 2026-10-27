package com.toastedvr.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.toastedvr.ai.domain.KnowledgeLevel;
import com.toastedvr.ai.domain.RoastingResult;

// Datos de la sesión de tueste que envía el backend para generar la
// retroalimentación. Los getters tienen los mismos nombres que los de
// RoastingSession en el backend, para que el prompt y la query de RAG se
// armen con la misma lógica que antes de separar el servicio.
public class RoastFeedbackRequest {

    private final RoastingResult result;
    private final Integer qualityScore;
    private final Double chargeTemperature;
    private final Double targetTemperature;
    private final Double finalTemperature;
    private final Integer totalDurationSeconds;
    private final Boolean firstCrackReached;
    private final Integer developmentTimeSeconds;
    private final KnowledgeLevel knowledgeLevel;

    @JsonCreator
    public RoastFeedbackRequest(
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
        this.result = result;
        this.qualityScore = qualityScore;
        this.chargeTemperature = chargeTemperature;
        this.targetTemperature = targetTemperature;
        this.finalTemperature = finalTemperature;
        this.totalDurationSeconds = totalDurationSeconds;
        this.firstCrackReached = firstCrackReached;
        this.developmentTimeSeconds = developmentTimeSeconds;
        this.knowledgeLevel = knowledgeLevel;
    }

    public RoastingResult getResult() { return result; }
    public Integer getQualityScore() { return qualityScore; }
    public Double getChargeTemperature() { return chargeTemperature; }
    public Double getTargetTemperature() { return targetTemperature; }
    public Double getFinalTemperature() { return finalTemperature; }
    public Integer getTotalDurationSeconds() { return totalDurationSeconds; }
    public Boolean isFirstCrackReached() { return firstCrackReached; }
    public Integer getDevelopmentTimeSeconds() { return developmentTimeSeconds; }
    public KnowledgeLevel getKnowledgeLevel() { return knowledgeLevel; }
}
