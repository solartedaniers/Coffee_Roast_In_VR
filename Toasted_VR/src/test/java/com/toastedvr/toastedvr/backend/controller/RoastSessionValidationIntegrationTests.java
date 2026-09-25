package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.AuditService;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF015: el servidor rechaza las sesiones que el simulador no puede producir
// y acepta todas las que sí produce. Los escenarios reales salen del simulador
// del frontend (roast-session-scenarios.json); un test de Jest comprueba que
// el evaluador sigue dando esos mismos resultados.
@SpringBootTest
@AutoConfigureMockMvc
class RoastSessionValidationIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final String SESSIONS_PATH = "/api/v1/roasting/sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoastingSessionRepository roastingSessionRepository;

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
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldAcceptEveryRealSimulatorScenarioAtEveryLevel() throws Exception {
        JsonNode scenarios = loadScenarios();
        int saved = 0;

        for (JsonNode scenario : scenarios) {
            for (KnowledgeLevel level : KnowledgeLevel.values()) {
                player.updateKnowledgeLevel(level);
                userRepository.save(player);
                JsonNode expected = scenario.at("/expected/" + level.name());

                postSession(toPayload(scenario.get("sim"), expected))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.result").value(expected.get("result").asText()))
                    .andExpect(jsonPath("$.qualityScore").value(expected.get("score").asInt()));
                saved++;
            }
        }

        assertThat(scenarios.size()).isGreaterThanOrEqualTo(20);
        assertThat(roastingSessionRepository.count()).isEqualTo(saved);
        verify(auditService, never()).logRoastSessionRejected(any(), anyString(), anyString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedPayloads")
    void shouldRejectWithoutSavingAndAuditTheRule(String rule, Map<String, Object> changes) throws Exception {
        Map<String, Object> payload = new HashMap<>(validPayload());
        payload.putAll(changes);

        postSession(payload)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ROAST_SESSION_REJECTED"))
            .andExpect(jsonPath("$.details.rule").value(rule))
            .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(roastingSessionRepository.count()).isZero();
        verify(auditService).logRoastSessionRejected(eq(player.getId()), eq(rule), anyString());
    }

    @Test
    void shouldSendTheRejectionMessageToTheClient() throws Exception {
        Map<String, Object> payload = new HashMap<>(validPayload());
        payload.put("qualityScore", 90);
        payload.put("result", "BURNED");
        payload.put("finalTemperature", 225.0);
        payload.put("peakTemperature", 226.0);

        postSession(payload)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "No se guardó la sesión: el puntaje 90 no es posible para el resultado BURNED (debe estar entre 5 y 50)."
            ));
    }

    @Test
    void shouldRejectMalformedJsonWith400WithoutAuditing() throws Exception {
        mockMvc.perform(
                post(SESSIONS_PATH)
                    .header("Authorization", "Bearer " + token())
                    .contentType(jsonMediaType())
                    .content("{\"chargeTemperature\": 190.0,")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(roastingSessionRepository.count()).isZero();
        verify(auditService, never()).logRoastSessionRejected(any(), anyString(), anyString());
    }

    @Test
    void shouldRejectUnknownResultWith400WithoutAuditing() throws Exception {
        Map<String, Object> payload = new HashMap<>(validPayload());
        payload.put("result", "EXCELLENT");

        postSession(payload)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(auditService, never()).logRoastSessionRejected(any(), anyString(), anyString());
    }

    @Test
    void shouldRejectMissingFieldsWith400WithoutAuditing() throws Exception {
        Map<String, Object> payload = new HashMap<>(validPayload());
        payload.remove("finalTemperature");

        postSession(payload)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.fieldErrors.finalTemperature").exists());

        verify(auditService, never()).logRoastSessionRejected(any(), anyString(), anyString());
    }

    static Stream<Arguments> rejectedPayloads() {
        return Stream.of(
            Arguments.of("CHARGE_TEMPERATURE_RANGE", Map.of("chargeTemperature", 751.0)),
            Arguments.of("TARGET_TEMPERATURE_RANGE", Map.of("targetTemperature", 240.0)),
            Arguments.of("FINAL_TEMPERATURE_LIMIT", Map.of(
                "finalTemperature", 260.0, "peakTemperature", 260.0, "result", "BURNED", "qualityScore", 5
            )),
            Arguments.of("PEAK_BELOW_FINAL", Map.of("peakTemperature", 200.0)),
            Arguments.of("DEVELOPMENT_TIME_EXCEEDS_TOTAL", Map.of("developmentTimeSeconds", 900)),
            Arguments.of("RESULT_WITHOUT_FIRST_CRACK", Map.of("firstCrackReached", false)),
            Arguments.of("RESULT_BELOW_RAW_CEILING", Map.of("finalTemperature", 170.0)),
            Arguments.of("RESULT_ABOVE_BURNED_CEILING", Map.of("finalTemperature", 219.0, "peakTemperature", 220.0)),
            Arguments.of("SCORE_OUT_OF_RANGE_FOR_RESULT", Map.of("result", "RAW", "qualityScore", 95))
        );
    }

    // Mismo mapeo que handleDischarge en RoastingSimulation.js.
    private Map<String, Object> toPayload(JsonNode sim, JsonNode expected) {
        double elapsed = sim.get("roastingElapsedSeconds").asDouble();
        boolean firstCrackReached = sim.get("firstCrackReached").asBoolean();
        Map<String, Object> payload = new HashMap<>();
        payload.put("chargeTemperature", sim.get("chargeTemperature").asDouble());
        payload.put("targetTemperature", sim.get("targetTemperature").asDouble());
        payload.put("totalDurationSeconds", Math.max(1, Math.round(elapsed)));
        payload.put("finalTemperature", sim.get("finalTemperature").asDouble());
        payload.put("peakTemperature", sim.get("peakTemperature").asDouble());
        payload.put("result", expected.get("result").asText());
        payload.put("qualityScore", expected.get("score").asInt());
        payload.put("firstCrackReached", firstCrackReached);
        payload.put("developmentTimeSeconds", firstCrackReached
            ? Math.max(0, Math.round(elapsed - sim.path("firstCrackTimeSeconds").asDouble(0)))
            : 0);
        return payload;
    }

    private Map<String, Object> validPayload() {
        return Map.of(
            "chargeTemperature", 190.0,
            "targetTemperature", 200.0,
            "totalDurationSeconds", 780,
            "finalTemperature", 205.0,
            "peakTemperature", 206.0,
            "result", "PERFECT",
            "qualityScore", 75,
            "firstCrackReached", true,
            "developmentTimeSeconds", 150
        );
    }

    private ResultActions postSession(Map<String, Object> payload) throws Exception {
        return mockMvc.perform(
            post(SESSIONS_PATH)
                .header("Authorization", "Bearer " + token())
                .contentType(jsonMediaType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(payload)))
        );
    }

    private JsonNode loadScenarios() throws Exception {
        try (InputStream input = new ClassPathResource("roast-session-scenarios.json").getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private String token() {
        return jwtService.generateToken(new UserPrincipal(player));
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }
}
