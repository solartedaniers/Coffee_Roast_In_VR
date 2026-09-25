package com.toastedvr.toastedvr.backend.dto;

import java.time.Instant;

// Una posición del ranking. Por privacidad solo lleva el username: nunca el
// nombre ni el correo del jugador.
public record RankingEntryResponse(int position, String username, int bestScore, Instant achievedAt) {
}
