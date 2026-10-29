package com.toastedvr.toastedvr.backend.dto;

// Resumen del historial con la nota que vale de cada sesión (la combinada si existe): sin
// sesiones, bestScore y averageScore son null.
public record SessionHistorySummaryResponse(Integer bestScore, Double averageScore, Long totalSessions) {
}
