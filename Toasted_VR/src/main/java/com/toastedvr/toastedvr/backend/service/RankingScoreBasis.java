package com.toastedvr.toastedvr.backend.service;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Con qué nota se arma el ranking: la combinada, solo de las sesiones desde una fecha. Las fotos
// guardadas con otra base no se comparan, para no dar avisos falsos en la corona al cambiarla.
@Component
public class RankingScoreBasis {

    private static final String COMBINED_SINCE = "COMBINED_SINCE_";

    private final LocalDateTime combinedSince;

    public RankingScoreBasis(@Value("${app.roasting.ranking-combined-since:2026-10-28T00:00:00}") LocalDateTime combinedSince) {
        this.combinedSince = combinedSince;
    }

    public LocalDateTime combinedSince() {
        return combinedSince;
    }

    public String id() {
        return COMBINED_SINCE + combinedSince;
    }
}
