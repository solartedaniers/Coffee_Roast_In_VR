package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotNull;

// El rango (0 a 100) lo valida el simulador con las constantes de la web.
public record SimulationGasRequest(@NotNull Double gasPercent) {
}
