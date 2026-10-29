package com.toastedvr.toastedvr.backend.domain;

// Nota final de una sesión con los pesos con que se calculó, para que el historial no cambie si
// después cambia la configuración.
public record CombinedScore(double rubricScore, int finalScore, double rulesWeight, double aiWeight, boolean defectCapApplied) {
}
