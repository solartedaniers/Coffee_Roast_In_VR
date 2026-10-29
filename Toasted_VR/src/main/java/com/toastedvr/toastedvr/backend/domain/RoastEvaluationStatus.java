package com.toastedvr.toastedvr.backend.domain;

// PENDING espera a que el servicio IA califique (con reintentos); FAILED agotó los reintentos.
public enum RoastEvaluationStatus {
    PENDING,
    COMPLETED,
    NOT_APPLICABLE,
    FAILED
}
