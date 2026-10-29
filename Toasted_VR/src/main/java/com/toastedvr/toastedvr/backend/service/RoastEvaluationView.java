package com.toastedvr.toastedvr.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.RoastEvaluationResponse;
import java.util.List;
import org.springframework.stereotype.Component;

// Cómo se muestra la calificación de una sesión: la nota que vale (la final si existe, si no la de
// las reglas), su calificación por rango y el desglose de la evaluación.
@Component
public class RoastEvaluationView {

    private final RoastGradeResolver gradeResolver;
    private final ObjectMapper objectMapper;

    public RoastEvaluationView(RoastGradeResolver gradeResolver, ObjectMapper objectMapper) {
        this.gradeResolver = gradeResolver;
        this.objectMapper = objectMapper;
    }

    /** Sesiones viejas (sin evaluación), pendientes o fallidas: vale la nota de las reglas. */
    public int scoreOf(RoastingSession session, RoastEvaluation evaluation) {
        return evaluation != null && evaluation.getFinalScore() != null ? evaluation.getFinalScore() : session.getQualityScore();
    }

    public String gradeOf(RoastingSession session, RoastEvaluation evaluation) {
        return gradeResolver.resolve(scoreOf(session, evaluation)).name();
    }

    public RoastEvaluationResponse toResponse(RoastingSession session, RoastEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        StoredCriteria stored = readCriteria(evaluation.getCriteriaJson());
        return new RoastEvaluationResponse(
            evaluation.getStatus().name(),
            evaluation.getSkipReason() == null ? null : evaluation.getSkipReason().name(),
            evaluation.getFinalScore(),
            session.getQualityScore(),
            evaluation.getRubricScore(),
            evaluation.getRulesWeight(),
            evaluation.getAiWeight(),
            evaluation.getDefectCapApplied(),
            evaluation.getRubricVersion(),
            evaluation.getModel(),
            stored.criteria(),
            stored.notApplicable()
        );
    }

    private StoredCriteria readCriteria(String json) {
        if (json == null) {
            return new StoredCriteria(List.of(), List.of());
        }
        try {
            StoredCriteria stored = objectMapper.readValue(json, StoredCriteria.class);
            return new StoredCriteria(
                stored.criteria() == null ? List.of() : stored.criteria(),
                stored.notApplicable() == null ? List.of() : stored.notApplicable());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored evaluation criteria.", exception);
        }
    }

    private record StoredCriteria(List<RoastEvaluationResponse.Criterion> criteria, List<String> notApplicable) {
    }
}
