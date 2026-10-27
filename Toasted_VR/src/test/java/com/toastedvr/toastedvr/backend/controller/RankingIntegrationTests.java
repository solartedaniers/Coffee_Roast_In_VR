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
import java.time.LocalDateTime;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF017: ranking por nivel con el mejor puntaje de cada jugador.
@SpringBootTest
@AutoConfigureMockMvc
class RankingIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 1, 12, 0);

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User viewer;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        viewer = saveUser("viewerUser", Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRankEachPlayerByTheirBestScoreAndBreakTiesByTheEarliestDate() throws Exception {
        User ana = saveUser("anaUser", Role.PLAYER, KnowledgeLevel.BEGINNER);
        User beto = saveUser("betoUser", Role.PLAYER, KnowledgeLevel.BEGINNER);
        saveSession(ana, KnowledgeLevel.INTERMEDIATE, 60, 1);
        saveSession(ana, KnowledgeLevel.INTERMEDIATE, 88, 5);
        // Beto también tiene 88, pero lo logró antes que Ana.
        saveSession(beto, KnowledgeLevel.INTERMEDIATE, 88, 3);
        saveSession(beto, KnowledgeLevel.INTERMEDIATE, 88, 9);
        saveSession(viewer, KnowledgeLevel.INTERMEDIATE, 70, 2);

        ranking(viewer, "?level=INTERMEDIATE")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.level").value("INTERMEDIATE"))
            .andExpect(jsonPath("$.top.length()").value(3))
            .andExpect(jsonPath("$.top[0].position").value(1))
            .andExpect(jsonPath("$.top[0].username").value("betoUser"))
            .andExpect(jsonPath("$.top[0].bestScore").value(88))
            .andExpect(jsonPath("$.top[0].achievedAt").value("2026-09-01T12:03:00Z"))
            .andExpect(jsonPath("$.top[1].username").value("anaUser"))
            .andExpect(jsonPath("$.top[1].achievedAt").value("2026-09-01T12:05:00Z"))
            .andExpect(jsonPath("$.top[2].username").value("viewerUser"))
            .andExpect(jsonPath("$.me.position").value(3))
            .andExpect(jsonPath("$.me.bestScore").value(70))
            .andExpect(jsonPath("$.hasSessionsInLevel").value(true));
    }

    @Test
    // Además del movimiento (▲▼/Nuevo), nada que identifique al jugador más allá del username.
    void shouldSendOnlyPositionUsernameScoreAndDate() throws Exception {
        saveSession(viewer, KnowledgeLevel.INTERMEDIATE, 70, 1);

        JsonNode entry = readJson(ranking(viewer, "")).at("/top/0");

        assertThat(entry.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("position", "username", "bestScore", "achievedAt", "movement");
    }

    @Test
    void shouldLeaveOutBlockedPlayersAdminsSessionsWithoutLevelAndOtherLevels() throws Exception {
        User blocked = saveUser("blockedUser", Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
        blocked.block();
        userRepository.save(blocked);
        User admin = saveUser("adminUser", Role.ADMIN, KnowledgeLevel.INTERMEDIATE);
        User legacy = saveUser("legacyUser", Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
        User advanced = saveUser("advancedUser", Role.PLAYER, KnowledgeLevel.ADVANCED);
        saveSession(blocked, KnowledgeLevel.INTERMEDIATE, 99, 1);
        saveSession(admin, KnowledgeLevel.INTERMEDIATE, 98, 1);
        saveSession(legacy, null, 97, 1);
        saveSession(advanced, KnowledgeLevel.ADVANCED, 96, 1);
        saveSession(viewer, KnowledgeLevel.INTERMEDIATE, 40, 1);

        ranking(viewer, "")
            .andExpect(jsonPath("$.top.length()").value(1))
            .andExpect(jsonPath("$.top[0].username").value("viewerUser"))
            .andExpect(jsonPath("$.me.position").value(1));
    }

    @Test
    void shouldShowTheViewerPositionEvenOutsideTheTopTen() throws Exception {
        for (int player = 0; player < 11; player++) {
            User rival = saveUser("rival" + player, Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
            saveSession(rival, KnowledgeLevel.INTERMEDIATE, 80 + player, player);
        }
        saveSession(viewer, KnowledgeLevel.INTERMEDIATE, 50, 1);

        ranking(viewer, "")
            .andExpect(jsonPath("$.top.length()").value(10))
            .andExpect(jsonPath("$.top[0].username").value("rival10"))
            .andExpect(jsonPath("$.top[9].position").value(10))
            .andExpect(jsonPath("$.me.position").value(12))
            .andExpect(jsonPath("$.me.username").value("viewerUser"))
            .andExpect(jsonPath("$.me.bestScore").value(50))
            .andExpect(jsonPath("$.hasSessionsInLevel").value(true));
    }

    @Test
    void shouldUseTheViewerLevelByDefaultAndAllowAnotherOne() throws Exception {
        User beginner = saveUser("beginnerUser", Role.PLAYER, KnowledgeLevel.BEGINNER);
        saveSession(beginner, KnowledgeLevel.BEGINNER, 77, 1);
        saveSession(viewer, KnowledgeLevel.INTERMEDIATE, 55, 1);

        ranking(viewer, "")
            .andExpect(jsonPath("$.level").value("INTERMEDIATE"))
            .andExpect(jsonPath("$.top[0].username").value("viewerUser"));

        ranking(viewer, "?level=BEGINNER")
            .andExpect(jsonPath("$.level").value("BEGINNER"))
            .andExpect(jsonPath("$.top[0].username").value("beginnerUser"))
            .andExpect(jsonPath("$.me").isEmpty())
            .andExpect(jsonPath("$.hasSessionsInLevel").value(false));
    }

    @Test
    void shouldSayWhenTheViewerHasNoSessionsInTheLevel() throws Exception {
        User rival = saveUser("rivalUser", Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
        saveSession(rival, KnowledgeLevel.INTERMEDIATE, 90, 1);
        // Una sesión vieja sin nivel no cuenta para el ranking.
        saveSession(viewer, null, 95, 1);

        ranking(viewer, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.top.length()").value(1))
            .andExpect(jsonPath("$.me").isEmpty())
            .andExpect(jsonPath("$.hasSessionsInLevel").value(false));
    }

    @Test
    void shouldReturnAnEmptyListWhenTheViewerHasNoLevelAndSendsNone() throws Exception {
        viewer.updateKnowledgeLevel(null);
        userRepository.save(Objects.requireNonNull(viewer));
        User rival = saveUser("rivalUser", Role.PLAYER, KnowledgeLevel.INTERMEDIATE);
        saveSession(rival, KnowledgeLevel.INTERMEDIATE, 90, 1);

        ranking(viewer, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.level").isEmpty())
            .andExpect(jsonPath("$.top.length()").value(0))
            .andExpect(jsonPath("$.me").isEmpty())
            .andExpect(jsonPath("$.hasSessionsInLevel").value(false));

        ranking(viewer, "?level=INTERMEDIATE").andExpect(jsonPath("$.top.length()").value(1));
    }

    @Test
    void shouldRejectAnUnknownLevelAndRequireAuthentication() throws Exception {
        ranking(viewer, "?level=EXPERTO")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/roasting/ranking")).andExpect(status().isUnauthorized());
    }

    private void saveSession(User owner, KnowledgeLevel level, int score, int minutesAfterBase) {
        RoastingSession session = roastingSessionRepository.save(new RoastingSession(
            owner, 190.0, 200.0, 780, 205.0, 206.0, RoastingResult.PERFECT, score, true, 150, level
        ));
        // Fecha fija para probar el desempate sin depender del reloj.
        jdbcTemplate.update(
            "UPDATE roasting_sessions SET created_at = ? WHERE id = ?",
            BASE_TIME.plusMinutes(minutesAfterBase),
            session.getId()
        );
    }

    private ResultActions ranking(User user, String query) throws Exception {
        return mockMvc.perform(
            get("/api/v1/roasting/ranking" + query)
                .header("Authorization", "Bearer " + jwtService.generateToken(new UserPrincipal(user)))
        );
    }

    private User saveUser(String username, Role role, KnowledgeLevel level) {
        User user = new User("Player", username.toLowerCase() + "@toastedvr.test", username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        user.updateKnowledgeLevel(level);
        return userRepository.save(Objects.requireNonNull(user));
    }

    private @NonNull JsonNode readJson(ResultActions resultActions) throws Exception {
        return Objects.requireNonNull(objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString()));
    }
}
