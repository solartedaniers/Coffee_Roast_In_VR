package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// C4 y C5: el simulador limita el puntaje según el resultado; los defectos
// (RAW, BURNED, BAKED) tienen su propio rango, más bajo que el de PERFECT.
@Component
@Order(9)
public class QualityScoreRule implements RoastSessionRule {

    static final String RULE = "SCORE_OUT_OF_RANGE_FOR_RESULT";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public QualityScoreRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        boolean perfect = request.result() == RoastingResult.PERFECT;
        int min = perfect ? properties.getPerfectScoreMin() : properties.getDefectScoreMin();
        int max = perfect ? properties.getPerfectScoreMax() : properties.getDefectScoreMax();
        int score = request.qualityScore();
        if (score >= min && score <= max) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.qualityScore",
            score,
            RoastResultLabel.of(request.result(), messages),
            min,
            max
        )));
    }
}
