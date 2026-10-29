package com.toastedvr.toastedvr.backend.dto;

import java.util.List;

// Curva y decisiones del jugador para la calificación de la IA (opcional en SaveSessionRequest).
// t es el segundo del tueste: cero en la carga. Los campos de texto no son enums para que un valor
// desconocido descarte solo la traza (RoastTraceInspector) y nunca rechace la sesión.
public record RoastTraceRequest(
    Integer version,
    String operationMode,
    Integer sampleIntervalSeconds,
    List<Sample> samples,
    List<GasChange> gasChanges,
    List<Event> events,
    List<Alert> alerts
) {

    public record Sample(Integer t, Double bean, Double gas) {
    }

    public record GasChange(Integer t, Double gas) {
    }

    public record Event(String type, Integer t) {
    }

    public record Alert(String id, Integer t) {
    }
}
