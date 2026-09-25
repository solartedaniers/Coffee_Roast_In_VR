package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.ApiInstants;
import com.toastedvr.toastedvr.backend.dto.RankingEntryResponse;
import com.toastedvr.toastedvr.backend.dto.RankingResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Ranking por nivel (RF017). Solo cuentan las sesiones con nivel guardado de
// jugadores activos que no son administradores. El puntaje de cada jugador es
// su mejor sesión en ese nivel; en empate gana quien lo logró antes.
@Service
public class RankingService {

    private final RoastingSessionRepository roastingSessionRepository;
    private final UserRepository userRepository;
    private final MessageResolver messages;
    private final int rankingSize;

    public RankingService(
        RoastingSessionRepository roastingSessionRepository,
        UserRepository userRepository,
        MessageResolver messages,
        @Value("${app.roasting.ranking-size:10}") int rankingSize
    ) {
        this.roastingSessionRepository = roastingSessionRepository;
        this.userRepository = userRepository;
        this.messages = messages;
        this.rankingSize = rankingSize;
    }

    /** Sin level se usa el nivel actual del jugador; si tampoco lo tiene, la lista viene vacía. */
    @Transactional
    public RankingResponse getRanking(Long userId, KnowledgeLevel requestedLevel) {
        KnowledgeLevel level = requestedLevel != null
            ? requestedLevel
            : userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new ResourceNotFoundException(messages.get("user.notFound")))
                .getKnowledgeLevel();
        if (level == null) {
            return new RankingResponse(null, List.of(), null, false);
        }

        // Las sesiones llegan de mejor a peor (y de la más antigua a la más
        // nueva en empate): la primera de cada jugador es su mejor marca.
        // ponytail: recorre todas las sesiones del nivel; si el volumen crece,
        // pasar a ROW_NUMBER() en SQL.
        Map<Long, RoastingSession> bestByUser = new LinkedHashMap<>();
        roastingSessionRepository.findRankingCandidates(level, Role.ADMIN)
            .forEach(session -> bestByUser.putIfAbsent(session.getUser().getId(), session));

        List<RankingEntryResponse> top = new ArrayList<>();
        RankingEntryResponse me = null;
        int position = 0;
        for (RoastingSession session : bestByUser.values()) {
            position++;
            RankingEntryResponse entry = new RankingEntryResponse(
                position,
                session.getUser().getUsername(),
                session.getQualityScore(),
                ApiInstants.from(session.getCreatedAt())
            );
            if (position <= rankingSize) {
                top.add(entry);
            }
            if (session.getUser().getId().equals(userId)) {
                me = entry;
            }
        }
        return new RankingResponse(level, top, me, me != null);
    }
}
