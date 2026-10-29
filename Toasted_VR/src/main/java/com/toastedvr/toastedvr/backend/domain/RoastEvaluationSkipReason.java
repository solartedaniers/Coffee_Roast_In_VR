package com.toastedvr.toastedvr.backend.domain;

// Por qué una sesión no tiene calificación de IA; su nota final queda solo con las reglas.
public enum RoastEvaluationSkipReason {
    NO_TRACE,
    INVALID_TRACE,
    AUTO_MODE,
    NO_CRITERIA
}
