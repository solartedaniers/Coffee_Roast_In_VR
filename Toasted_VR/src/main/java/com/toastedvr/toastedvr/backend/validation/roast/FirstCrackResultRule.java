package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// C1: sin primer crack el grano queda crudo. Solo en esta dirección: un
// resultado RAW con primer crack es válido (desarrollo interrumpido).
@Component
@Order(6)
public class FirstCrackResultRule implements RoastSessionRule {

    static final String RULE = "RESULT_WITHOUT_FIRST_CRACK";

    private final MessageResolver messages;

    public FirstCrackResultRule(MessageResolver messages) {
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        if (request.firstCrackReached() || request.result() == RoastingResult.RAW) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.withoutFirstCrack",
            RoastResultLabel.of(RoastingResult.RAW, messages),
            RoastResultLabel.of(request.result(), messages)
        )));
    }
}
