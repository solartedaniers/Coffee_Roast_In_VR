package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.Optional;

// Una regla de negocio que debe cumplir una sesión de tueste para guardarse
// (RF015). Cada regla revisa una sola cosa; RoastSessionValidator las aplica
// en el orden de @Order y rechaza con la primera que falle.
public interface RoastSessionRule {

    Optional<RoastSessionViolation> findViolation(SaveSessionRequest request);
}
