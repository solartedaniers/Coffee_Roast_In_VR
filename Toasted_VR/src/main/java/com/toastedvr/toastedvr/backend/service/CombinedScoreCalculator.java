package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.RoastEvaluationProperties;
import com.toastedvr.toastedvr.backend.domain.CombinedScore;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import org.springframework.stereotype.Component;

// Nota final = peso de reglas × qualityScore + peso de IA × nota de la rúbrica. Un defecto nunca
// pasa del tope, aunque las decisiones hayan sido buenas.
@Component
public class CombinedScoreCalculator {

    private static final double WEIGHT_SUM_TOLERANCE = 1e-9;

    private final RoastEvaluationProperties properties;

    public CombinedScoreCalculator(RoastEvaluationProperties properties) {
        if (properties.getRulesWeight() < 0 || properties.getAiWeight() < 0
            || Math.abs(properties.getRulesWeight() + properties.getAiWeight() - 1) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalStateException("app.roast-evaluation weights must be non-negative and add up to 1");
        }
        this.properties = properties;
    }

    public CombinedScore combine(RoastingSession session, double rubricScore) {
        double rulesWeight = properties.getRulesWeight();
        double aiWeight = properties.getAiWeight();
        int combined = (int) Math.round(rulesWeight * session.getQualityScore() + aiWeight * rubricScore);
        boolean capped = session.getResult() != RoastingResult.PERFECT && combined > properties.getDefectScoreCap();
        int finalScore = capped ? properties.getDefectScoreCap() : combined;
        return new CombinedScore(rubricScore, finalScore, rulesWeight, aiWeight, capped);
    }
}
