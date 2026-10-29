package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.RoastEvaluationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastGrade;
import org.springframework.stereotype.Component;

// Convierte una nota (0-100) en su calificación según los mínimos configurados de cada rango.
@Component
public class RoastGradeResolver {

    private final RoastEvaluationProperties properties;

    public RoastGradeResolver(RoastEvaluationProperties properties) {
        if (!(properties.getFairGradeMin() <= properties.getGoodGradeMin()
            && properties.getGoodGradeMin() <= properties.getExcellentGradeMin())) {
            throw new IllegalStateException("app.roast-evaluation grade minimums must be in order");
        }
        this.properties = properties;
    }

    public RoastGrade resolve(int score) {
        if (score >= properties.getExcellentGradeMin()) {
            return RoastGrade.EXCELLENT;
        }
        if (score >= properties.getGoodGradeMin()) {
            return RoastGrade.GOOD;
        }
        return score >= properties.getFairGradeMin() ? RoastGrade.FAIR : RoastGrade.POOR;
    }
}
