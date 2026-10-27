package com.toastedvr.toastedvr.backend.dto;

import jakarta.validation.constraints.NotBlank;

// signature es la que devolvió GET /roasting/ranking para el ranking mostrado.
public record RankingSeenRequest(
    @NotBlank(message = "{roasting.ranking.invalidSignature}")
    String signature
) {
}
