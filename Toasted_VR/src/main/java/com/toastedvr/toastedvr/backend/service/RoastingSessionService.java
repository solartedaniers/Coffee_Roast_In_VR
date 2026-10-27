package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.ApiInstants;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionHistoryItemResponse;
import com.toastedvr.toastedvr.backend.dto.SessionHistorySummaryResponse;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.validation.roast.RoastSessionValidator;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RoastingSessionService {

    private final RoastingSessionRepository roastingSessionRepository;
    private final UserRepository userRepository;
    private final MessageResolver messages;
    private final RoastSessionValidator roastSessionValidator;
    private final int historyPageSize;

    public RoastingSessionService(
        RoastingSessionRepository roastingSessionRepository,
        UserRepository userRepository,
        MessageResolver messages,
        RoastSessionValidator roastSessionValidator,
        @Value("${app.roasting.history-page-size:10}") int historyPageSize
    ) {
        this.roastingSessionRepository = roastingSessionRepository;
        this.userRepository = userRepository;
        this.messages = messages;
        this.roastSessionValidator = roastSessionValidator;
        this.historyPageSize = historyPageSize;
    }

    @Transactional
    public SessionResultResponse saveSession(Long userId, SaveSessionRequest request) {
        User user = userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("user.notFound")));

        // RF015: el puntaje lo calcula el cliente; si no es coherente no se guarda.
        roastSessionValidator.validate(userId, request);

        RoastingSession session = new RoastingSession(
            user,
            request.chargeTemperature(),
            request.targetTemperature(),
            request.totalDurationSeconds(),
            request.finalTemperature(),
            request.peakTemperature(),
            request.result(),
            request.qualityScore(),
            request.firstCrackReached(),
            request.developmentTimeSeconds(),
            user.getKnowledgeLevel()
        );

        RoastingSession saved = roastingSessionRepository.save(session);
        return toResponse(saved);
    }

    @Transactional
    public RoastingSession getOwnedSession(Long userId, Long sessionId) {
        RoastingSession session = roastingSessionRepository.findById(Objects.requireNonNull(sessionId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("roasting.session.notFound")));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(messages.get("roasting.session.notFound"));
        }

        // Fuerza la carga del User (perezoso) mientras la transacción sigue
        // activa. Con open-in-view desactivado, si el controller intenta leer
        // session.getUser().getKnowledgeLevel() después de que este método
        // retorna, la sesión de Hibernate ya está cerrada y lanza
        // LazyInitializationException — inicializarlo aquí lo deja en caché
        // en el proxy para que esa lectura posterior sea segura.
        session.getUser().getKnowledgeLevel();

        return session;
    }

    /** Sesiones del jugador, de la más reciente a la más antigua; result es un filtro opcional. */
    @Transactional
    public Page<SessionHistoryItemResponse> getHistory(Long userId, int page, RoastingResult result) {
        // El id desempata sesiones guardadas en el mismo instante.
        Pageable pageable = PageRequest.of(
            Math.max(0, page),
            historyPageSize,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<RoastingSession> sessions = result == null
            ? roastingSessionRepository.findByUserId(userId, pageable)
            : roastingSessionRepository.findByUserIdAndResult(userId, result, pageable);
        return sessions.map(this::toHistoryItem);
    }

    /** Mejor puntaje, promedio (un decimal) y total de sesiones del jugador. */
    @Transactional
    public SessionHistorySummaryResponse getSummary(Long userId) {
        SessionHistorySummaryResponse summary = roastingSessionRepository.summarizeByUserId(userId);
        Double average = summary.averageScore() == null ? null : Math.round(summary.averageScore() * 10) / 10.0;
        return new SessionHistorySummaryResponse(summary.bestScore(), average, summary.totalSessions());
    }

    @Transactional
    public SessionResultResponse getSessionDetail(Long userId, Long sessionId) {
        return toResponse(getOwnedSession(userId, sessionId));
    }

    private SessionHistoryItemResponse toHistoryItem(RoastingSession session) {
        return new SessionHistoryItemResponse(
            session.getId(),
            ApiInstants.from(session.getCreatedAt()),
            session.getKnowledgeLevel(),
            session.getResult().name(),
            session.getQualityScore(),
            session.getFinalTemperature(),
            session.getTotalDurationSeconds(),
            developmentTimeRatio(session)
        );
    }

    // DTR = tiempo de desarrollo / duración total, con 3 decimales. Sin first
    // crack no hubo desarrollo, y las sesiones viejas pueden no tener el dato.
    private Double developmentTimeRatio(RoastingSession session) {
        Integer development = session.getDevelopmentTimeSeconds();
        Integer total = session.getTotalDurationSeconds();
        if (!Boolean.TRUE.equals(session.isFirstCrackReached()) || development == null || total == null || total <= 0) {
            return null;
        }
        return Math.round(development * 1000.0 / total) / 1000.0;
    }

    private SessionResultResponse toResponse(RoastingSession session) {
        return new SessionResultResponse(
            session.getId(),
            session.getResult().name(),
            session.getQualityScore(),
            session.getChargeTemperature(),
            session.getTargetTemperature(),
            session.getTotalDurationSeconds(),
            session.getFinalTemperature(),
            session.getPeakTemperature(),
            session.isFirstCrackReached(),
            session.getDevelopmentTimeSeconds(),
            developmentTimeRatio(session),
            session.getKnowledgeLevel(),
            ApiInstants.from(session.getCreatedAt())
        );
    }
}
