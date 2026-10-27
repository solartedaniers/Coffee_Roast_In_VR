package com.toastedvr.toastedvr.backend.dto;

// Resumen del historial: sin sesiones, bestScore y averageScore son null.
public record SessionHistorySummaryResponse(Integer bestScore, Double averageScore, Long totalSessions) {
}
