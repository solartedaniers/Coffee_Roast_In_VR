package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// El desarrollo (desde el primer crack) es parte del tueste, no puede durar más.
@Component
@Order(5)
public class DevelopmentTimeRule implements RoastSessionRule {

    static final String RULE = "DEVELOPMENT_TIME_EXCEEDS_TOTAL";

    private final MessageResolver messages;

    public DevelopmentTimeRule(MessageResolver messages) {
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        Integer developmentTime = request.developmentTimeSeconds();
        if (developmentTime == null || developmentTime <= request.totalDurationSeconds()) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.developmentTime",
            developmentTime,
            request.totalDurationSeconds()
        )));
    }
}
