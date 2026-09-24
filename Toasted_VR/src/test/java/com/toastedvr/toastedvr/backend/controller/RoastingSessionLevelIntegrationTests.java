package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.OllamaFeedbackService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF009: el servidor guarda en cada sesión el nivel que el jugador tenía al
// terminarla, y la retroalimentación de IA usa ese nivel.
@SpringBootTest
@AutoConfigureMockMvc
class RoastingSessionLevelIntegrationTests {

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
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private OllamaFeedbackService ollamaFeedbackService;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "player@toastedvr.test", "playerUser", passwordEncoder.encode(PASSWORD));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.INTERMEDIATE);
        player = userRepository.save(player);
        when(ollamaFeedbackService.generateFeedback(any(), any())).thenReturn("ok");
    }

    // Los demás tests borran usuarios sin borrar sesiones; se limpia al salir
    // para no dejarles filas que rompan la llave foránea.
    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldStoreTheServerSideLevelAndIgnoreAnyLevelSentByTheClient() throws Exception {
        Map<String, Object> payload = new HashMap<>(validPayload());
        payload.put("knowledgeLevel", "ADVANCED");

        JsonNode body = save(payload);

        assertThat(body.get("knowledgeLevel").asText()).isEqualTo("INTERMEDIATE");
        RoastingSession stored = roastingSessionRepository.findById(requireId(body)).orElseThrow();
        assertThat(stored.getKnowledgeLevel()).isEqualTo(KnowledgeLevel.INTERMEDIATE);
    }

    @Test
    void shouldStoreNullLevelWhenTheUserHasNotChosenOne() throws Exception {
        player.updateKnowledgeLevel(null);
        userRepository.save(player);

        JsonNode body = save(validPayload());

        assertThat(body.get("knowledgeLevel").isNull()).isTrue();
        assertThat(roastingSessionRepository.findById(requireId(body)).orElseThrow().getKnowledgeLevel()).isNull();
    }

    @Test
    void shouldGenerateFeedbackWithTheLevelStoredInTheSessionEvenIfTheUserChangedIt() throws Exception {
        Long sessionId = requireId(save(validPayload()));
        player.updateKnowledgeLevel(KnowledgeLevel.ADVANCED);
        userRepository.save(player);

        requestFeedback(sessionId);

        verify(ollamaFeedbackService).generateFeedback(any(RoastingSession.class), eq(KnowledgeLevel.INTERMEDIATE));
    }

    @Test
    void shouldFallBackToTheCurrentUserLevelForSessionsWithoutStoredLevel() throws Exception {
        RoastingSession legacy = roastingSessionRepository.save(new RoastingSession(
            player, 190.0, 200.0, 780, 205.0, 206.0, RoastingResult.PERFECT, 90, true, 150, null
        ));

        requestFeedback(legacy.getId());

        verify(ollamaFeedbackService).generateFeedback(any(RoastingSession.class), eq(KnowledgeLevel.INTERMEDIATE));
    }

    private JsonNode save(Map<String, Object> payload) throws Exception {
        String response = mockMvc.perform(
                post("/api/v1/roasting/sessions")
                    .header("Authorization", "Bearer " + token())
                    .contentType(jsonMediaType())
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(payload)))
            )
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void requestFeedback(Long sessionId) throws Exception {
        mockMvc.perform(
                get("/api/v1/roasting/sessions/" + sessionId + "/feedback")
                    .header("Authorization", "Bearer " + token())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.feedback").value("ok"));
    }

    private Map<String, Object> validPayload() {
        return Map.of(
            "chargeTemperature", 190.0,
            "targetTemperature", 200.0,
            "totalDurationSeconds", 780,
            "finalTemperature", 205.0,
            "peakTemperature", 206.0,
            "result", "PERFECT",
            "qualityScore", 90,
            "firstCrackReached", true,
            "developmentTimeSeconds", 150
        );
    }

    private String token() {
        return jwtService.generateToken(new UserPrincipal(player));
    }

    private @NonNull Long requireId(JsonNode body) {
        return Objects.requireNonNull(body.get("id").asLong());
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }
}
