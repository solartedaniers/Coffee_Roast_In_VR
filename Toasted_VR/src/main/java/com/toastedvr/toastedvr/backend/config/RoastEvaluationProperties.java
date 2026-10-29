package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Calificación combinada: pesos de las reglas y de la rúbrica de la IA, y tope de la nota final
// cuando el resultado es un defecto (crudo, quemado u horneado).
@ConfigurationProperties(prefix = "app.roast-evaluation")
public class RoastEvaluationProperties {

    private double rulesWeight = 0.7;
    private double aiWeight = 0.3;
    private int defectScoreCap = 50;
    // Proceso en segundo plano que pide la calificación al servicio IA.
    private int batchSize = 5;
    private int maxAttempts = 8;
    private int baseRetrySeconds = 30;
    private int maxRetrySeconds = 3_600;
    // Nota mínima de cada calificación; por debajo de fairGradeMin es deficiente.
    private int excellentGradeMin = 85;
    private int goodGradeMin = 70;
    private int fairGradeMin = 50;

    public int getExcellentGradeMin() {
        return excellentGradeMin;
    }

    public void setExcellentGradeMin(int excellentGradeMin) {
        this.excellentGradeMin = excellentGradeMin;
    }

    public int getGoodGradeMin() {
        return goodGradeMin;
    }

    public void setGoodGradeMin(int goodGradeMin) {
        this.goodGradeMin = goodGradeMin;
    }

    public int getFairGradeMin() {
        return fairGradeMin;
    }

    public void setFairGradeMin(int fairGradeMin) {
        this.fairGradeMin = fairGradeMin;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBaseRetrySeconds() {
        return baseRetrySeconds;
    }

    public void setBaseRetrySeconds(int baseRetrySeconds) {
        this.baseRetrySeconds = baseRetrySeconds;
    }

    public int getMaxRetrySeconds() {
        return maxRetrySeconds;
    }

    public void setMaxRetrySeconds(int maxRetrySeconds) {
        this.maxRetrySeconds = maxRetrySeconds;
    }

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
