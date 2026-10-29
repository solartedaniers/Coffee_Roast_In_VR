package com.toastedvr.toastedvr.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.RoastSessionTrace;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import com.toastedvr.toastedvr.backend.validation.roast.RoastTraceInspector;
import com.toastedvr.toastedvr.backend.validation.roast.RoastTraceProblem;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

// Al guardar una sesión deja lista su calificación de IA: guarda la traza si sirve. Nunca hace
// fallar el guardado por la traza.
@Component
public class RoastEvaluationRegistrar {

    private final RoastTraceInspector inspector;
    private final RoastSessionTraceRepository traceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RoastEvaluationRegistrar(
        RoastTraceInspector inspector,
        RoastSessionTraceRepository traceRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.inspector = inspector;
        this.traceRepository = traceRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public void register(Long userId, RoastingSession session, SaveSessionRequest request) {
        if (request.roastTrace() == null) {
            return;
        }
        Optional<RoastTraceProblem> problem = inspector.findProblem(request);
        if (problem.isPresent()) {
            auditService.logRoastTraceDiscarded(userId, session.getId(), problem.get().name());
            return;
        }
        traceRepository.save(new RoastSessionTrace(session, writeJson(request)));
    }

    private String writeJson(SaveSessionRequest request) {
        try {
            return Objects.requireNonNull(objectMapper.writeValueAsString(request.roastTrace()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store the roast trace.", exception);
        }
    }
}
