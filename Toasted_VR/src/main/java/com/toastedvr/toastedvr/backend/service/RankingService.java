package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.ApiInstants;
import com.toastedvr.toastedvr.backend.dto.RankingChangesResponse;
import com.toastedvr.toastedvr.backend.dto.RankingEntryResponse;
import com.toastedvr.toastedvr.backend.dto.RankingMovementResponse;
import com.toastedvr.toastedvr.backend.dto.RankingResponse;
import com.toastedvr.toastedvr.backend.exception.InvalidRequestException;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.RankingSignatureService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Ranking por nivel (RF017). Solo cuentan las sesiones con nivel guardado de
// jugadores activos que no son administradores. El puntaje de cada jugador es
// su mejor sesión en ese nivel; en empate gana quien lo logró antes. Cada
// fila indica cómo se movió desde la última foto que el jugador vio.
@Service
public class RankingService {

    private final RoastingSessionRepository roastingSessionRepository;
    private final UserRepository userRepository;
    private final RankingSnapshotService snapshotService;
    private final RankingSignatureService signatureService;
    private final MessageResolver messages;
    private final int rankingSize;
    private final int pollIntervalSeconds;

    public RankingService(
        RoastingSessionRepository roastingSessionRepository,
        UserRepository userRepository,
        RankingSnapshotService snapshotService,
        RankingSignatureService signatureService,
        MessageResolver messages,
        @Value("${app.roasting.ranking-size:10}") int rankingSize,
        @Value("${app.roasting.ranking-poll-seconds:60}") int pollIntervalSeconds
    ) {
        this.roastingSessionRepository = roastingSessionRepository;
        this.userRepository = userRepository;
        this.snapshotService = snapshotService;
        this.signatureService = signatureService;
        this.messages = messages;
        this.rankingSize = rankingSize;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    /** Sin level se usa el nivel actual del jugador; si tampoco lo tiene, la lista viene vacía. */
    @Transactional
    public RankingResponse getRanking(Long userId, KnowledgeLevel requestedLevel) {
        KnowledgeLevel level = requestedLevel != null ? requestedLevel : findUser(userId).getKnowledgeLevel();
        if (level == null) {
            return new RankingResponse(null, List.of(), null, false, null);
        }

        List<RankedPlayer> ranked = rankPlayers(level);
        // Sin foto previa (primera visita) no hay flechas.
        Optional<RankingPhoto> previous = snapshotService.find(userId, level);

        List<RankingEntryResponse> top = new ArrayList<>();
        RankingEntryResponse me = null;
        for (RankedPlayer player : ranked) {
            boolean isViewer = player.userId().equals(userId);
            if (player.position() <= rankingSize) {
                top.add(player.toEntry(previous.map(photo -> photo.movementOf(player.userId(), player.position())).orElse(null)));
            }
            if (isViewer) {
                me = player.toEntry(previous.map(photo -> photo.ownMovement(player.position())).orElse(null));
            }
        }

        String signature = signatureService.sign(userId, level, photoOf(ranked, userId));
        return new RankingResponse(level, top, me, me != null, signature);
    }

    /** Guarda como vista exactamente la foto firmada, aunque el ranking ya haya cambiado. */
    @Transactional
    public void markSeen(Long userId, String signature) {
        RankingSignatureService.SignedPhoto signed = signatureService.verify(signature, userId)
            .orElseThrow(() -> new InvalidRequestException(messages.get("roasting.ranking.invalidSignature")));
        snapshotService.save(findUser(userId), signed.level(), signed.photo());
    }

    /** Punto de aviso: solo hay cambios si existe una foto previa del nivel del jugador y es distinta. */
    @Transactional
    public RankingChangesResponse getChanges(Long userId) {
        User user = findUser(userId);
        KnowledgeLevel level = user.getKnowledgeLevel();
        if (level == null || user.getRole() == Role.ADMIN) {
            return new RankingChangesResponse(level, false, pollIntervalSeconds);
        }
        boolean hasChanges = snapshotService.find(userId, level)
            .map(seen -> photoOf(rankPlayers(level), userId).differsFrom(seen))
            .orElse(false);
        return new RankingChangesResponse(level, hasChanges, pollIntervalSeconds);
    }

    // Las sesiones llegan de mejor a peor (y de la más antigua a la más nueva
    // en empate): la primera de cada jugador es su mejor marca.
    // ponytail: recorre todas las sesiones del nivel; si el volumen crece,
    // pasar a ROW_NUMBER() en SQL.
    private List<RankedPlayer> rankPlayers(KnowledgeLevel level) {
        Map<Long, RoastingSession> bestByUser = new LinkedHashMap<>();
        roastingSessionRepository.findRankingCandidates(level, Role.ADMIN)
            .forEach(session -> bestByUser.putIfAbsent(session.getUser().getId(), session));

        List<RankedPlayer> ranked = new ArrayList<>();
        int position = 0;
        for (RoastingSession session : bestByUser.values()) {
            position++;
            ranked.add(new RankedPlayer(
                session.getUser().getId(),
                session.getUser().getUsername(),
                session.getQualityScore(),
                ApiInstants.from(session.getCreatedAt()),
                position
            ));
        }
        return ranked;
    }

    private RankingPhoto photoOf(List<RankedPlayer> ranked, Long viewerId) {
        Map<Long, Integer> topPositions = new LinkedHashMap<>();
        Integer ownPosition = null;
        for (RankedPlayer player : ranked) {
            if (player.position() <= rankingSize) {
                topPositions.put(player.userId(), player.position());
            }
            if (player.userId().equals(viewerId)) {
                ownPosition = player.position();
            }
        }
        return new RankingPhoto(topPositions, ownPosition);
    }

    private User findUser(Long userId) {
        return userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("user.notFound")));
    }

    private record RankedPlayer(Long userId, String username, int bestScore, Instant achievedAt, int position) {

        RankingEntryResponse toEntry(RankingMovementResponse movement) {
            return new RankingEntryResponse(position, username, bestScore, achievedAt, movement);
        }
    }
}
