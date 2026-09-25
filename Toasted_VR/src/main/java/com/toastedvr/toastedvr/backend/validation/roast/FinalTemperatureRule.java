package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// El simulador no deja que el grano pase de su temperatura máxima segura.
@Component
@Order(3)
public class FinalTemperatureRule implements RoastSessionRule {

    static final String RULE = "FINAL_TEMPERATURE_LIMIT";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public FinalTemperatureRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        double finalTemperature = request.finalTemperature();
        if (finalTemperature <= properties.getFinalTemperatureMax()) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.finalTemperature",
            finalTemperature,
            properties.getFinalTemperatureMax()
        )));
    }
}
