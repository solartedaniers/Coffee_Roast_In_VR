package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;

// Consulta liviana del punto de aviso (RF017): si el ranking del nivel del
// jugador cambió desde la última vez que lo vio, y cada cuántos segundos
// conviene volver a preguntar.
public record RankingChangesResponse(KnowledgeLevel level, boolean hasChanges, int pollIntervalSeconds) {
}
