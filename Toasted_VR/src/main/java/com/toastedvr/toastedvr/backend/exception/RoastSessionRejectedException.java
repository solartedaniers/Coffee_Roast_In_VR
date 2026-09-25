package com.toastedvr.toastedvr.backend.exception;

import java.util.Map;

// Sesión de tueste que no cumple una regla de negocio (RF015). details.rule
// lleva el código de la regla para que cualquier cliente (web o Unity) sepa
// cuál falló sin leer el texto.
public class RoastSessionRejectedException extends ApiException {

    public RoastSessionRejectedException(String rule, String message) {
        super(ErrorCode.ROAST_SESSION_REJECTED, message, Map.of("rule", rule), null);
    }
}
