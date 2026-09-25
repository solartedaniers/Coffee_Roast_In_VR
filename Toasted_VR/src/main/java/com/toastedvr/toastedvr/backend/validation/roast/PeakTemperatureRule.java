package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// La temperatura máxima alcanzada nunca puede ser menor que la final.
@Component
@Order(4)
public class PeakTemperatureRule implements RoastSessionRule {

    static final String RULE = "PEAK_BELOW_FINAL";

    private final MessageResolver messages;

    public PeakTemperatureRule(MessageResolver messages) {
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        if (request.peakTemperature() >= request.finalTemperature()) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.peakTemperature",
            request.peakTemperature(),
            request.finalTemperature()
        )));
    }
}
