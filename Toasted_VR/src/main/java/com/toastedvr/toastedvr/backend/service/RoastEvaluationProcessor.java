package com.toastedvr.toastedvr.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.AiEvaluationClient;
import com.toastedvr.toastedvr.backend.client.AiEvaluationRejectedException;
import com.toastedvr.toastedvr.backend.client.AiEvaluationReply;
import com.toastedvr.toastedvr.backend.config.RoastEvaluationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationSkipReason;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationStatus;
import com.toastedvr.toastedvr.backend.domain.RoastSessionTrace;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// Pide al servicio IA la calificación de las evaluaciones pendientes. Si Groq o el servicio fallan,
// la deja para más tarde con una espera que se duplica en cada intento.
// ponytail: un solo backend procesa la cola; con varias instancias haría falta bloquear las filas.
@Service
public class RoastEvaluationProcessor {

    private static final Logger log = LoggerFactory.getLogger(RoastEvaluationProcessor.class);

    private final RoastEvaluationRepository evaluationRepository;
    private final RoastSessionTraceRepository traceRepository;
    private final AiEvaluationClient client;
    private final CombinedScoreCalculator calculator;
    private final RoastEvaluationProperties properties;
    private final ObjectMapper objectMapper;

    public RoastEvaluationProcessor(
        RoastEvaluationRepository evaluationRepository,
        RoastSessionTraceRepository traceRepository,
        AiEvaluationClient client,
        CombinedScoreCalculator calculator,
        RoastEvaluationProperties properties,
        ObjectMapper objectMapper
    ) {
        this.evaluationRepository = evaluationRepository;
        this.traceRepository = traceRepository;
        this.client = client;
        this.calculator = calculator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Sin transacción abierta mientras espera al servicio IA; cada evaluación se guarda por separado. */
    public int processDue() {
        List<RoastEvaluation> due = evaluationRepository.findDue(
            RoastEvaluationStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, properties.getBatchSize()));
        due.forEach(this::process);
        return due.size();
    }

    private void process(RoastEvaluation evaluation) {
        Optional<RoastSessionTrace> trace = traceRepository.findBySessionId(evaluation.getSession().getId());
        if (trace.isEmpty()) {
            evaluation.skip(RoastEvaluationSkipReason.NO_TRACE, null, null, LocalDateTime.now());
        } else {
            grade(evaluation, trace.get().getTraceJson());
        }
        evaluationRepository.save(evaluation);
    }

    private void grade(RoastEvaluation evaluation, String traceJson) {
        try {
            AiEvaluationReply reply = client.evaluate(traceJson);
            LocalDateTime now = LocalDateTime.now();
            String criteria = criteriaJson(reply);
            if (reply.completed()) {
                evaluation.complete(calculator.combine(evaluation.getSession(), reply.score()),
                    reply.rubricVersion(), reply.model(), criteria, now);
            } else {
                evaluation.skip(RoastEvaluationSkipReason.NO_CRITERIA, reply.rubricVersion(), criteria, now);
            }
        } catch (AiEvaluationRejectedException exception) {
            log.warn("El servicio IA rechazó la evaluación {}: {}", evaluation.getId(), exception.getMessage());
            evaluation.fail(exception.getMessage(), LocalDateTime.now());
        } catch (RuntimeException exception) {
            retryOrFail(evaluation, exception);
        }
    }

    private void retryOrFail(RoastEvaluation evaluation, RuntimeException exception) {
        int attempt = evaluation.getAttempts() + 1;
        LocalDateTime now = LocalDateTime.now();
        if (attempt >= properties.getMaxAttempts()) {
            log.warn("La evaluación {} agotó sus {} intentos: {}", evaluation.getId(), attempt, exception.getMessage());
            evaluation.fail(exception.getMessage(), now);
            return;
        }
        evaluation.retryLater(exception.getMessage(), now.plusSeconds(retryDelaySeconds(attempt)));
    }

    long retryDelaySeconds(int attempt) {
        long delay = (long) properties.getBaseRetrySeconds() << Math.min(attempt - 1, Integer.SIZE - 2);
        return Math.min(delay, properties.getMaxRetrySeconds());
    }

    private String criteriaJson(AiEvaluationReply reply) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "criteria", Objects.requireNonNullElse(reply.criteria(), List.of()),
                "notApplicable", Objects.requireNonNullElse(reply.notApplicable(), List.of())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store the evaluation criteria.", exception);
        }
    }
}
