package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// La temperatura objetivo solo puede venir del rango que ofrece el simulador.
@Component
@Order(2)
public class TargetTemperatureRule implements RoastSessionRule {

    static final String RULE = "TARGET_TEMPERATURE_RANGE";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public TargetTemperatureRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        double target = request.targetTemperature();
        if (target >= properties.getTargetTemperatureMin() && target <= properties.getTargetTemperatureMax()) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.targetTemperature",
            target,
            properties.getTargetTemperatureMin(),
            properties.getTargetTemperatureMax()
        )));
    }
}
