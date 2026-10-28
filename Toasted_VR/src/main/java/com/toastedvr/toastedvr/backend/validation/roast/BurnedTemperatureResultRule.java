package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// C3: si la temperatura máxima pasó del techo del tueste medio, un grano
// que no quedó crudo está quemado, aunque después se haya enfriado. Solo en
// esta dirección: el segundo crack o la subida agresiva con el grano seco
// queman con una temperatura máxima menor.
@Component
@Order(8)
public class BurnedTemperatureResultRule implements RoastSessionRule {

    static final String RULE = "RESULT_ABOVE_BURNED_CEILING";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public BurnedTemperatureResultRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        RoastingResult result = request.result();
        if (request.peakTemperature() <= properties.getBurnedTemperatureCeiling()
            || result == RoastingResult.RAW
            || result == RoastingResult.BURNED) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.aboveBurnedCeiling",
            RoastResultLabel.of(result, messages),
            request.peakTemperature(),
            properties.getBurnedTemperatureCeiling()
        )));
    }
}
