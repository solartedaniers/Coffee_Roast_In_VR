package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toastedvr.toastedvr.backend.config.RoastEvaluationProperties;
import com.toastedvr.toastedvr.backend.domain.CombinedScore;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Los casos son los de la tabla de pesos del plan de la fase 5 (70/30 con tope de 50), con la nota
// de la rúbrica redondeada a un decimal como la entrega el servicio IA.
class CombinedScoreCalculatorTests {

    private final CombinedScoreCalculator calculator = new CombinedScoreCalculator(new RoastEvaluationProperties());

    private static RoastingSession session(RoastingResult result, int qualityScore) {
        return new RoastingSession(null, 190.0, 200.0, 780, 205.0, 206.0, result, qualityScore, true, 150, null);
    }

    @ParameterizedTest(name = "{0} {1} + rúbrica {2} = {3}")
    @CsvSource({
        "PERFECT, 74, 93.3, 80, false",
        "PERFECT, 74, 26.7, 60, false",
        "PERFECT, 49, 73.3, 56, false",
        "PERFECT, 38, 40.0, 39, false",
        "BURNED, 17, 13.3, 16, false",
        "BURNED, 17, 66.7, 32, false",
        "RAW, 30, 44.4, 34, false",
        "BAKED, 45, 33.3, 41, false",
        "PERFECT, 96, 100.0, 97, false",
        "BURNED, 50, 100.0, 50, true",
    })
    void combinesWithSeventyThirtyAndCapsDefects(RoastingResult result, int quality, double rubric, int expected, boolean capped) {
        CombinedScore score = calculator.combine(session(result, quality), rubric);

        assertThat(score.finalScore()).isEqualTo(expected);
        assertThat(score.defectCapApplied()).isEqualTo(capped);
        assertThat(score.rulesWeight()).isEqualTo(0.7);
        assertThat(score.aiWeight()).isEqualTo(0.3);
        assertThat(score.rubricScore()).isEqualTo(rubric);
    }

    @Test
    void neverCapsADevelopedRoast() {
        assertThat(calculator.combine(session(RoastingResult.PERFECT, 60), 90).finalScore()).isEqualTo(69);
    }

    @Test
    void usesTheConfiguredWeightsAndCap() {
        RoastEvaluationProperties properties = new RoastEvaluationProperties();
        properties.setRulesWeight(0.5);
        properties.setAiWeight(0.5);
        properties.setDefectScoreCap(40);
        CombinedScoreCalculator configured = new CombinedScoreCalculator(properties);

        assertThat(configured.combine(session(RoastingResult.PERFECT, 74), 26.7).finalScore()).isEqualTo(50);
        assertThat(configured.combine(session(RoastingResult.RAW, 30), 100).finalScore()).isEqualTo(40);
    }

    @Test
    void refusesWeightsThatDoNotAddUpToOne() {
        RoastEvaluationProperties properties = new RoastEvaluationProperties();
        properties.setAiWeight(0.4);

        assertThatThrownBy(() -> new CombinedScoreCalculator(properties)).isInstanceOf(IllegalStateException.class);
    }
}
