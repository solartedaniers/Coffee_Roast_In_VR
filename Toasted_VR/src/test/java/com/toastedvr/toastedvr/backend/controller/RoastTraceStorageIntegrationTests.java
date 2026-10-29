package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluation;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationSkipReason;
import com.toastedvr.toastedvr.backend.domain.RoastEvaluationStatus;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.AuditService;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// La traza es opcional y nunca impide guardar la sesión: si no cuadra se descarta y se audita. Cada
// sesión nueva queda con su calificación de IA pendiente o marcada como no aplicable.
@SpringBootTest
@AutoConfigureMockMvc
class RoastTraceStorageIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoastingSessionRepository roastingSessionRepository;

    @Autowired
    private RoastSessionTraceRepository traceRepository;

    @Autowired
    private RoastEvaluationRepository evaluationRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private AuditService auditService;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "player@toastedvr.test", "playerUser", passwordEncoder.encode(PASSWORD));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.INTERMEDIATE);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldStoreACoherentTraceWithTheSession() throws Exception {
        long sessionId = savedId(postSession(payload(RoastTracePayloads.coherent(780, 630, 205.0, "MANUAL"))));

        JsonNode stored = objectMapper.readTree(traceRepository.findBySessionId(sessionId).orElseThrow().getTraceJson());
        assertThat(stored.get("operationMode").asText()).isEqualTo("MANUAL");
        assertThat(stored.get("samples").size()).isEqualTo(157);
        RoastEvaluation evaluation = evaluationOf(sessionId);
        assertThat(evaluation.getStatus()).isEqualTo(RoastEvaluationStatus.PENDING);
        assertThat(evaluation.getFinalScore()).isNull();
        assertThat(evaluation.getNextAttemptAt()).isNotNull();
        verify(auditService, never()).logRoastTraceDiscarded(any(), any(), anyString());
    }

    @Test
    void shouldSaveTheSessionButDiscardAndAuditATraceThatDoesNotMatch() throws Exception {
        long sessionId = savedId(postSession(payload(RoastTracePayloads.coherent(780, 600, 205.0, "MANUAL"))));

        assertThat(traceRepository.findBySessionId(sessionId)).isEmpty();
        verify(auditService).logRoastTraceDiscarded(eq(player.getId()), eq(sessionId), eq("FIRST_CRACK_MISMATCH"));
        assertSkipped(sessionId, RoastEvaluationSkipReason.INVALID_TRACE);
    }

    @Test
    void shouldSaveTheSessionWithAnUnknownModeInsteadOfRejectingIt() throws Exception {
        long sessionId = savedId(postSession(payload(RoastTracePayloads.coherent(780, 630, 205.0, "REMOTE"))));

        assertThat(traceRepository.findBySessionId(sessionId)).isEmpty();
        verify(auditService).logRoastTraceDiscarded(eq(player.getId()), eq(sessionId), eq("UNKNOWN_OPERATION_MODE"));
    }

    @Test
    void shouldWorkAsBeforeWithoutATrace() throws Exception {
        Map<String, Object> payload = payload(null);
        payload.remove("roastTrace");

        long sessionId = savedId(postSession(payload));

        assertThat(traceRepository.findBySessionId(sessionId)).isEmpty();
        verify(auditService, never()).logRoastTraceDiscarded(any(), any(), anyString());
        assertSkipped(sessionId, RoastEvaluationSkipReason.NO_TRACE);
    }

    @Test
    void shouldKeepTheAutoModeTraceWithoutAnAiEvaluation() throws Exception {
        long sessionId = savedId(postSession(payload(RoastTracePayloads.coherent(780, 630, 205.0, "AUTO"))));

        assertThat(traceRepository.findBySessionId(sessionId)).isPresent();
        assertSkipped(sessionId, RoastEvaluationSkipReason.AUTO_MODE);
    }

    private RoastEvaluation evaluationOf(long sessionId) {
        return evaluationRepository.findBySessionId(sessionId).orElseThrow();
    }

    // Sin calificación de IA la nota final es la de las reglas.
    private void assertSkipped(long sessionId, RoastEvaluationSkipReason reason) {
        RoastEvaluation evaluation = evaluationOf(sessionId);
        assertThat(evaluation.getStatus()).isEqualTo(RoastEvaluationStatus.NOT_APPLICABLE);
        assertThat(evaluation.getSkipReason()).isEqualTo(reason);
        assertThat(evaluation.getFinalScore()).isEqualTo(75);
        assertThat(evaluation.getNextAttemptAt()).isNull();
    }

    // Mismo mapeo que handleDischarge en RoastingSimulation.js, con la traza que arma el simulador.
    @Test
    void shouldKeepTheTraceOfEverySharedSimulatorScenario() throws Exception {
        JsonNode scenarios;
        try (InputStream input = new ClassPathResource("roast-session-scenarios.json").getInputStream()) {
            scenarios = objectMapper.readTree(input);
        }
        for (JsonNode scenario : scenarios) {
            JsonNode sim = scenario.get("sim");
            JsonNode expected = scenario.at("/expected/INTERMEDIATE");
            double elapsed = sim.get("roastingElapsedSeconds").asDouble();
            int total = (int) Math.max(1, Math.round(elapsed));
            boolean firstCrack = sim.get("firstCrackReached").asBoolean();
            int development = firstCrack
                ? (int) Math.max(0, Math.round(elapsed - sim.path("firstCrackTimeSeconds").asDouble(0)))
                : 0;
            Map<String, Object> payload = new HashMap<>();
            payload.put("chargeTemperature", sim.get("chargeTemperature").asDouble());
            payload.put("targetTemperature", sim.get("targetTemperature").asDouble());
            payload.put("totalDurationSeconds", total);
            payload.put("finalTemperature", sim.get("finalTemperature").asDouble());
            payload.put("peakTemperature", sim.get("peakTemperature").asDouble());
            payload.put("result", expected.get("result").asText());
            payload.put("qualityScore", expected.get("score").asInt());
            payload.put("firstCrackReached", firstCrack);
            payload.put("developmentTimeSeconds", development);
            payload.put("roastTrace", RoastTracePayloads.coherent(
                total, firstCrack ? total - development : null, sim.get("finalTemperature").asDouble(), "MANUAL"));

            assertThat(traceRepository.findBySessionId(savedId(postSession(payload))))
                .as(scenario.get("name").asText()).isPresent();
        }
        verify(auditService, never()).logRoastTraceDiscarded(any(), any(), anyString());
    }

    private Map<String, Object> payload(Map<String, Object> trace) {
        Map<String, Object> payload = new HashMap<>(Map.of(
            "chargeTemperature", 190.0,
            "targetTemperature", 200.0,
            "totalDurationSeconds", 780,
            "finalTemperature", 205.0,
            "peakTemperature", 206.0,
            "result", "PERFECT",
            "qualityScore", 75,
            "firstCrackReached", true,
            "developmentTimeSeconds", 150
        ));
        payload.put("roastTrace", trace);
        return payload;
    }

    @Test
    void shouldAnswerTheSaveWithThePendingEvaluationAndTheRulesScoreMeanwhile() throws Exception {
        postSession(payload(RoastTracePayloads.coherent(780, 630, 205.0, "MANUAL")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.finalScore").value(75))
            .andExpect(jsonPath("$.grade").value("GOOD"))
            .andExpect(jsonPath("$.evaluation.status").value("PENDING"));
        postSession(payload(RoastTracePayloads.coherent(780, 630, 205.0, "AUTO")))
            .andExpect(jsonPath("$.evaluation.status").value("NOT_APPLICABLE"))
            .andExpect(jsonPath("$.evaluation.skipReason").value("AUTO_MODE"))
            .andExpect(jsonPath("$.evaluation.finalScore").value(75));
    }

    private long savedId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions postSession(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(
            post("/api/v1/roasting/sessions")
                .header("Authorization", "Bearer " + jwtService.generateToken(new UserPrincipal(player)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(payload)))
        );
    }
}
