package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.AiEvaluationClient;
import com.toastedvr.toastedvr.backend.client.AiEvaluationRejectedException;
import com.toastedvr.toastedvr.backend.client.AiEvaluationReply;
import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationSkipReason;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationStatus;
import com.toastedvr.toastedvr.backend.domain.RoastSessionTrace;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class RoastEvaluationProcessorIntegrationTests {

    private static final String TRACE = "{\"version\":1,\"operationMode\":\"MANUAL\"}";
    private static final AiEvaluationReply COMPLETED = new AiEvaluationReply("COMPLETED", "draft-1",
        "openai/gpt-oss-120b", 93.3,
        List.of(new AiEvaluationReply.Criterion("DRYING_GAS", 3, 3, "Mantuviste la subida en franja.")),
        List.of("ALERT_REACTION"));

    @Autowired
    private RoastEvaluationProcessor processor;

    @Autowired
    private RoastEvaluationRepository evaluationRepository;

    @Autowired
    private RoastSessionTraceRepository traceRepository;

    @Autowired
    private RoastingSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiEvaluationClient client;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "player@toastedvr.test", "playerUser", "hash");
        player.assignRole(Role.PLAYER);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private RoastEvaluation pendingEvaluation(RoastingResult result, int qualityScore) {
        RoastingSession session = sessionRepository.save(new RoastingSession(
            player, 190.0, 200.0, 780, 205.0, 206.0, result, qualityScore, true, 150, null));
        traceRepository.save(new RoastSessionTrace(session, TRACE));
        return evaluationRepository.save(Objects.requireNonNull(RoastEvaluation.pending(session, LocalDateTime.now().minusSeconds(1))));
    }

    private RoastEvaluation reload(RoastEvaluation evaluation) {
        return evaluationRepository.findById(Objects.requireNonNull(evaluation.getId())).orElseThrow();
    }

    @Test
    void storesTheCombinedScoreWithItsWeightsAndCriteria() throws Exception {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.PERFECT, 74);
        when(client.evaluate(TRACE)).thenReturn(COMPLETED);

        assertThat(processor.processDue()).isEqualTo(1);

        RoastEvaluation done = reload(evaluation);
        assertThat(done.getStatus()).isEqualTo(RoastEvaluationStatus.COMPLETED);
        assertThat(done.getFinalScore()).isEqualTo(80);
        assertThat(done.getRubricScore()).isEqualTo(93.3);
        assertThat(done.getRulesWeight()).isEqualTo(0.7);
        assertThat(done.getAiWeight()).isEqualTo(0.3);
        assertThat(done.getDefectCapApplied()).isFalse();
        assertThat(done.getRubricVersion()).isEqualTo("draft-1");
        assertThat(done.getModel()).isEqualTo("openai/gpt-oss-120b");
        assertThat(objectMapper.readTree(done.getCriteriaJson()).at("/criteria/0/justification").asText())
            .isEqualTo("Mantuviste la subida en franja.");
        assertThat(done.getNextAttemptAt()).isNull();
        assertThat(done.getCompletedAt()).isNotNull();
    }

    @Test
    void neverEvaluatesASessionTwice() {
        pendingEvaluation(RoastingResult.PERFECT, 74);
        when(client.evaluate(TRACE)).thenReturn(COMPLETED);

        processor.processDue();
        assertThat(processor.processDue()).isZero();

        verify(client, times(1)).evaluate(anyString());
    }

    @Test
    void capsADefectEvenWithAPerfectRubric() {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.BURNED, 50);
        when(client.evaluate(TRACE)).thenReturn(new AiEvaluationReply("COMPLETED", "draft-1", "m", 100.0, List.of(), List.of()));

        processor.processDue();

        assertThat(reload(evaluation).getFinalScore()).isEqualTo(50);
        assertThat(reload(evaluation).getDefectCapApplied()).isTrue();
    }

    @Test
    void keepsTheRulesScoreWhenNoCriterionApplies() {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.RAW, 30);
        when(client.evaluate(TRACE)).thenReturn(new AiEvaluationReply("NOT_APPLICABLE", "draft-1", null, null, List.of(),
            List.of("DRYING_GAS")));

        processor.processDue();

        RoastEvaluation done = reload(evaluation);
        assertThat(done.getStatus()).isEqualTo(RoastEvaluationStatus.NOT_APPLICABLE);
        assertThat(done.getSkipReason()).isEqualTo(RoastEvaluationSkipReason.NO_CRITERIA);
        assertThat(done.getFinalScore()).isEqualTo(30);
    }

    @Test
    void retriesLaterWithAGrowingWaitWhenGroqFails() {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.PERFECT, 74);
        when(client.evaluate(TRACE)).thenThrow(new AiServiceUnavailableException("Groq sin cuota", null));
        LocalDateTime before = LocalDateTime.now();

        processor.processDue();

        RoastEvaluation waiting = reload(evaluation);
        assertThat(waiting.getStatus()).isEqualTo(RoastEvaluationStatus.PENDING);
        assertThat(waiting.getAttempts()).isEqualTo(1);
        assertThat(waiting.getLastError()).isEqualTo("Groq sin cuota");
        assertThat(waiting.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(30));
        assertThat(waiting.getFinalScore()).isNull();
        // Todavía no le toca: no se vuelve a llamar al servicio IA.
        assertThat(processor.processDue()).isZero();
        assertThat(processor.retryDelaySeconds(2)).isEqualTo(60);
        assertThat(processor.retryDelaySeconds(5)).isEqualTo(480);
        assertThat(processor.retryDelaySeconds(40)).isEqualTo(3_600);
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.PERFECT, 74);
        when(client.evaluate(TRACE)).thenThrow(new AiServiceUnavailableException("caído", null));

        for (int attempt = 0; attempt < 8; attempt++) {
            processor.processDue();
            jdbcTemplate.update("UPDATE roast_evaluations SET next_attempt_at = ? WHERE id = ?",
                LocalDateTime.now().minusSeconds(1), evaluation.getId());
        }

        RoastEvaluation failed = reload(evaluation);
        assertThat(failed.getStatus()).isEqualTo(RoastEvaluationStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(8);
        assertThat(failed.getFinalScore()).isNull();
        verify(client, times(8)).evaluate(anyString());
    }

    @Test
    void doesNotRetryATraceTheServiceRejected() {
        RoastEvaluation evaluation = pendingEvaluation(RoastingResult.PERFECT, 74);
        when(client.evaluate(TRACE)).thenThrow(new AiEvaluationRejectedException("400", null));

        processor.processDue();

        assertThat(reload(evaluation).getStatus()).isEqualTo(RoastEvaluationStatus.FAILED);
    }

    @Test
    void skipsAPendingEvaluationWithoutTrace() {
        RoastingSession session = sessionRepository.save(new RoastingSession(
            player, 190.0, 200.0, 780, 205.0, 206.0, RoastingResult.PERFECT, 74, true, 150, null));
        RoastEvaluation evaluation = evaluationRepository.save(Objects.requireNonNull(RoastEvaluation.pending(session, LocalDateTime.now())));

        processor.processDue();

        assertThat(reload(evaluation).getSkipReason()).isEqualTo(RoastEvaluationSkipReason.NO_TRACE);
        verify(client, never()).evaluate(anyString());
    }
}
