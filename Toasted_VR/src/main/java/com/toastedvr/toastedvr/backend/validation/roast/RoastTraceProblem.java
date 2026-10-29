package com.toastedvr.toastedvr.backend.validation.roast;

// Motivo por el que se descarta la traza de una sesión; queda en la auditoría.
public enum RoastTraceProblem {
    UNSUPPORTED_VERSION,
    UNKNOWN_OPERATION_MODE,
    TOO_LARGE,
    INVALID_SAMPLES,
    INVALID_GAS_CHANGES,
    INVALID_EVENTS,
    INVALID_ALERTS,
    PEAK_MISMATCH,
    FIRST_CRACK_MISMATCH,
    DISCHARGE_MISMATCH
}
