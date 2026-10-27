package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.util.List;

// Ranking de un nivel (RF017). level es null cuando el jugador no eligió
// nivel y no pidió uno; me es null si el jugador no tiene sesiones que
// cuenten en ese nivel. signature identifica exactamente este ranking
// mostrado: se envía a PUT /roasting/ranking/seen para marcarlo como visto.
public record RankingResponse(
    KnowledgeLevel level,
    List<RankingEntryResponse> top,
    RankingEntryResponse me,
    boolean hasSessionsInLevel,
    String signature
) {
}
