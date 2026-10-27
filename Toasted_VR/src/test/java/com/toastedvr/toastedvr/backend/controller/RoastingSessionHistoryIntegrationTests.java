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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF016: cada jugador ve solo sus sesiones, de la más reciente a la más
// antigua, 10 por página, con filtro por resultado, resumen y detalle.
@SpringBootTest
@AutoConfigureMockMvc
class RoastingSessionHistoryIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final String UTC_INSTANT_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

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

    private User player;
    private User otherPlayer;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = saveUser("playerUser", "player@toastedvr.test");
        otherPlayer = saveUser("otherUser", "other@toastedvr.test");
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldListOnlyTheOwnSessionsFromNewestToOldestTenPerPage() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int score = 60; score < 72; score++) {
            ids.add(saveSession(player, RoastingResult.PERFECT, score, KnowledgeLevel.INTERMEDIATE).getId());
        }
        saveSession(otherPlayer, RoastingResult.PERFECT, 99, KnowledgeLevel.ADVANCED);

        JsonNode firstPage = readJson(history(player, "").andExpect(status().isOk()));
        assertThat(firstPage.get("content")).hasSize(10);
        assertThat(firstPage.at("/totalElements").asLong()).isEqualTo(12);
        assertThat(firstPage.at("/totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.at("/content/0/id").asLong()).isEqualTo(ids.get(11));
        assertThat(firstPage.at("/content/9/id").asLong()).isEqualTo(ids.get(2));

        JsonNode secondPage = readJson(history(player, "?page=1").andExpect(status().isOk()));
        assertThat(secondPage.get("content")).hasSize(2);
        assertThat(secondPage.at("/content/1/id").asLong()).isEqualTo(ids.get(0));
        secondPage.get("content").forEach(item -> assertThat(item.get("qualityScore").asInt()).isNotEqualTo(99));
    }

    @Test
    void shouldFilterByResult() throws Exception {
        saveSession(player, RoastingResult.PERFECT, 80, KnowledgeLevel.BEGINNER);
        saveSession(player, RoastingResult.BURNED, 10, KnowledgeLevel.BEGINNER);
        saveSession(player, RoastingResult.BURNED, 20, KnowledgeLevel.BEGINNER);
        saveSession(otherPlayer, RoastingResult.BURNED, 30, KnowledgeLevel.BEGINNER);

        history(player, "?result=BURNED")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].result").value("BURNED"))
            .andExpect(jsonPath("$.content[0].qualityScore").value(20))
            .andExpect(jsonPath("$.content[1].qualityScore").value(10));
    }

    @Test
    void shouldSendEveryColumnOfARow() throws Exception {
        saveSession(player, RoastingResult.PERFECT, 75, KnowledgeLevel.ADVANCED);

        JsonNode row = readJson(history(player, "")).at("/content/0");

        assertThat(row.get("createdAt").asText()).matches(UTC_INSTANT_PATTERN);
        assertThat(row.get("knowledgeLevel").asText()).isEqualTo("ADVANCED");
        assertThat(row.get("result").asText()).isEqualTo("PERFECT");
        assertThat(row.get("qualityScore").asInt()).isEqualTo(75);
        assertThat(row.get("finalTemperature").asDouble()).isEqualTo(205.0);
        assertThat(row.get("totalDurationSeconds").asInt()).isEqualTo(780);
        // 150 s de desarrollo / 780 s totales.
        assertThat(row.get("developmentTimeRatio").asDouble()).isEqualTo(0.192);
    }

    @Test
    void shouldSendNullLevelAndNullRatioWhenThereIsNoData() throws Exception {
        roastingSessionRepository.save(new RoastingSession(
            player, 190.0, 200.0, 300, 162.0, 162.0, RoastingResult.RAW, 33, false, 0, null
        ));

        history(player, "")
            .andExpect(jsonPath("$.content[0].knowledgeLevel").isEmpty())
            .andExpect(jsonPath("$.content[0].developmentTimeRatio").isEmpty());
    }

    @Test
    void shouldSummarizeBestAndAverageScore() throws Exception {
        saveSession(player, RoastingResult.PERFECT, 80, KnowledgeLevel.BEGINNER);
        saveSession(player, RoastingResult.RAW, 33, KnowledgeLevel.BEGINNER);
        saveSession(player, RoastingResult.BURNED, 5, KnowledgeLevel.BEGINNER);
        saveSession(otherPlayer, RoastingResult.PERFECT, 100, KnowledgeLevel.BEGINNER);

        summary(player)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bestScore").value(80))
            .andExpect(jsonPath("$.averageScore").value(39.3))
            .andExpect(jsonPath("$.totalSessions").value(3));
    }

    @Test
    void shouldSendAnEmptySummaryWhenThePlayerHasNoSessions() throws Exception {
        summary(player)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bestScore").isEmpty())
            .andExpect(jsonPath("$.averageScore").isEmpty())
            .andExpect(jsonPath("$.totalSessions").value(0));
        history(player, "")
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldShowTheDetailOfAnOwnSession() throws Exception {
        RoastingSession session = saveSession(player, RoastingResult.PERFECT, 75, KnowledgeLevel.INTERMEDIATE);

        detail(player, session.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(session.getId()))
            .andExpect(jsonPath("$.chargeTemperature").value(190.0))
            .andExpect(jsonPath("$.targetTemperature").value(200.0))
            .andExpect(jsonPath("$.peakTemperature").value(206.0))
            .andExpect(jsonPath("$.firstCrackReached").value(true))
            .andExpect(jsonPath("$.developmentTimeSeconds").value(150))
            .andExpect(jsonPath("$.developmentTimeRatio").value(0.192))
            .andExpect(jsonPath("$.knowledgeLevel").value("INTERMEDIATE"));
    }

    @Test
    void shouldHideTheSessionsOfOtherPlayers() throws Exception {
        RoastingSession foreign = saveSession(otherPlayer, RoastingResult.PERFECT, 90, KnowledgeLevel.BEGINNER);

        detail(player, foreign.getId())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Sesión de tueste no encontrada."));
        detail(player, 999_999L).andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidParametersWithBadRequest() throws Exception {
        history(player, "?result=EXCELENTE")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/roasting/sessions/abc").header("Authorization", bearer(player)))
            .andExpect(status().isBadRequest());
        history(player, "?page=-3").andExpect(status().isOk());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/roasting/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/sessions/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/sessions/1")).andExpect(status().isUnauthorized());
    }

    private RoastingSession saveSession(User owner, RoastingResult result, int score, KnowledgeLevel level) {
        return roastingSessionRepository.save(new RoastingSession(
            owner, 190.0, 200.0, 780, 205.0, 206.0, result, score, true, 150, level
        ));
    }

    private ResultActions history(User user, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/roasting/sessions" + query).header("Authorization", bearer(user)));
    }

    private ResultActions summary(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/roasting/sessions/summary").header("Authorization", bearer(user)));
    }

    private ResultActions detail(User user, Long sessionId) throws Exception {
        return mockMvc.perform(get("/api/v1/roasting/sessions/" + sessionId).header("Authorization", bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }

    private User saveUser(String username, String email) {
        User user = new User("Player", email, username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(Role.PLAYER);
        return userRepository.save(Objects.requireNonNull(user));
    }

    private @NonNull JsonNode readJson(ResultActions resultActions) throws Exception {
        return Objects.requireNonNull(objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString()));
    }
}
