package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class RoastingSessionService {

    private final RoastingSessionRepository roastingSessionRepository;
    private final UserRepository userRepository;

    public RoastingSessionService(
        RoastingSessionRepository roastingSessionRepository,
        UserRepository userRepository
    ) {
        this.roastingSessionRepository = roastingSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SessionResultResponse saveSession(Long userId, SaveSessionRequest request) {
        User user = userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

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
            request.developmentTimeSeconds()
        );

        RoastingSession saved = roastingSessionRepository.save(session);
        return toResponse(saved);
    }

    @Transactional
    public RoastingSession getOwnedSession(Long userId, Long sessionId) {
        RoastingSession session = roastingSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Sesión de tueste no encontrada."));

        if (!session.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Sesión de tueste no encontrada.");
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

    private SessionResultResponse toResponse(RoastingSession session) {
        return new SessionResultResponse(
            session.getId(),
            session.getResult().name(),
            session.getQualityScore(),
            session.getTargetTemperature(),
            session.getTotalDurationSeconds(),
            session.getFinalTemperature(),
            session.getPeakTemperature(),
            session.isFirstCrackReached(),
            session.getDevelopmentTimeSeconds(),
            session.getCreatedAt()
        );
    }
}
