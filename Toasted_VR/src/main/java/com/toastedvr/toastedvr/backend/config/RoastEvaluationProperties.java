package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Calificación combinada: pesos de las reglas y de la rúbrica de la IA, y tope de la nota final
// cuando el resultado es un defecto (crudo, quemado u horneado).
@ConfigurationProperties(prefix = "app.roast-evaluation")
public class RoastEvaluationProperties {

    private double rulesWeight = 0.7;
    private double aiWeight = 0.3;
    private int defectScoreCap = 50;

    public double getRulesWeight() {
        return rulesWeight;
    }

    public void setRulesWeight(double rulesWeight) {
        this.rulesWeight = rulesWeight;
    }

    public double getAiWeight() {
        return aiWeight;
    }

    public void setAiWeight(double aiWeight) {
        this.aiWeight = aiWeight;
    }

    public int getDefectScoreCap() {
        return defectScoreCap;
    }

    public void setDefectScoreCap(int defectScoreCap) {
        this.defectScoreCap = defectScoreCap;
    }
}
