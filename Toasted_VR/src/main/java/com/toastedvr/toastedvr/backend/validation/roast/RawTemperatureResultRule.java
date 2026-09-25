package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// C2: por debajo del techo de crudo el grano queda crudo, sin importar lo
// demás. Por encima puede ser cualquier resultado.
@Component
@Order(7)
public class RawTemperatureResultRule implements RoastSessionRule {

    static final String RULE = "RESULT_BELOW_RAW_CEILING";

    private final RoastValidationProperties properties;
    private final MessageResolver messages;

    public RawTemperatureResultRule(RoastValidationProperties properties, MessageResolver messages) {
        this.properties = properties;
        this.messages = messages;
    }

    @Override
    public Optional<RoastSessionViolation> findViolation(SaveSessionRequest request) {
        if (request.finalTemperature() >= properties.getRawTemperatureCeiling()
            || request.result() == RoastingResult.RAW) {
            return Optional.empty();
        }
        return Optional.of(new RoastSessionViolation(RULE, messages.get(
            "roasting.session.rejected.belowRawCeiling",
            request.result(),
            request.finalTemperature(),
            properties.getRawTemperatureCeiling()
        )));
    }
}
