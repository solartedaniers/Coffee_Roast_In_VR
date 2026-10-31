package com.toastedvr.toastedvr.backend.dto;

// Opcionales: sin ellos el simulador usa los valores por defecto de la web. Los rangos los valida el
// simulador con las mismas constantes de RoastConstants.js.
public record SimulationStartRequest(Double chargeTemperature, Double targetTemperature) {
}
