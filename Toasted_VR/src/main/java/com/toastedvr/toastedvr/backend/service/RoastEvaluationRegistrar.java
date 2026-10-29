package com.toastedvr.toastedvr.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationSkipReason;
import com.toastedvr.toastedvr.backend.domain.RoastSessionTrace;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import com.toastedvr.toastedvr.backend.validation.roast.RoastTraceInspector;
import com.toastedvr.toastedvr.backend.validation.roast.RoastTraceProblem;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Al guardar una sesión deja lista su calificación de IA: guarda la traza si sirve y la deja
// pendiente para el proceso en segundo plano, o sin calificación de IA si no hay decisiones que
// calificar. Nunca hace fallar el guardado por la traza.
@Component
public class RoastEvaluationRegistrar {

    private static final String AUTO_MODE = "AUTO";

    private final RoastTraceInspector inspector;
    private final RoastSessionTraceRepository traceRepository;
    private final RoastEvaluationRepository evaluationRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RoastEvaluationRegistrar(
        RoastTraceInspector inspector,
        RoastSessionTraceRepository traceRepository,
        RoastEvaluationRepository evaluationRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.inspector = inspector;
        this.traceRepository = traceRepository;
        this.evaluationRepository = evaluationRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public RoastEvaluation register(Long userId, RoastingSession session, SaveSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (request.roastTrace() == null) {
            return skip(session, RoastEvaluationSkipReason.NO_TRACE, now);
        }
        Optional<RoastTraceProblem> problem = inspector.findProblem(request);
        if (problem.isPresent()) {
            auditService.logRoastTraceDiscarded(userId, session.getId(), problem.get().name());
            return skip(session, RoastEvaluationSkipReason.INVALID_TRACE, now);
        }
        traceRepository.save(new RoastSessionTrace(session, writeJson(request)));
        // En AUTO el gas lo mueve el simulador: no hay decisiones del jugador que calificar.
        if (AUTO_MODE.equals(request.roastTrace().operationMode())) {
            return skip(session, RoastEvaluationSkipReason.AUTO_MODE, now);
        }
        return evaluationRepository.save(RoastEvaluation.pending(session, now));
    }

    private RoastEvaluation skip(RoastingSession session, RoastEvaluationSkipReason reason, LocalDateTime now) {
        return evaluationRepository.save(RoastEvaluation.notApplicable(session, reason, now));
    }

    private String writeJson(SaveSessionRequest request) {
        try {
            return Objects.requireNonNull(objectMapper.writeValueAsString(request.roastTrace()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store the roast trace.", exception);
        }
    }
}
