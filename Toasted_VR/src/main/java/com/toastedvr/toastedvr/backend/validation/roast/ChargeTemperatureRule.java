package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// La carga es la temperatura del aire al cargar el grano; basta con que sea
// alcanzable por el modelo térmico (no se limita al rango del slider).
@Component
@Order(1)
public class ChargeTemperatureRule implements RoastSessionRule {

    static final String RULE = "CHARGE_TEMPERATURE_RANGE";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public ChargeTemperatureRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        double charge = request.chargeTemperature();
        if (charge >= properties.getChargeTemperatureMin() && charge <= properties.getChargeTemperatureMax()) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.chargeTemperature",
            charge,
            properties.getChargeTemperatureMin(),
            properties.getChargeTemperatureMax()
        )));
    }
}
