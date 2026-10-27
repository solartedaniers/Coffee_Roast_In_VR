package com.toastedvr.toastedvr.backend.validation.roast;

// rule es el código estable que ven los clientes y la auditoría; message es
// el texto para el jugador.
public record RoastSessionViolation(String rule, String message) {
}
