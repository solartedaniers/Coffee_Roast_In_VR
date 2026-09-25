package com.toastedvr.toastedvr.backend.controller;

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
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF020: el administrador ve el puntaje promedio general y por nivel, y el
// nivel y la temperatura objetivo de cada sesión. Lo que ya mostraba no cambia.
@SpringBootTest
@AutoConfigureMockMvc
class AdminStatsIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

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

    private User admin;
    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        admin = saveUser("adminUser", Role.ADMIN);
        player = saveUser("playerUser", Role.PLAYER);
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSendTheGeneralAverageAndTheAverageByLevelGroupingSessionsWithoutLevel() throws Exception {
        saveSession(KnowledgeLevel.BEGINNER, RoastingResult.PERFECT, 80, 200.0);
        saveSession(KnowledgeLevel.BEGINNER, RoastingResult.PERFECT, 71, 200.0);
        saveSession(KnowledgeLevel.ADVANCED, RoastingResult.BURNED, 5, 210.0);
        saveSession(null, RoastingResult.RAW, 33, 200.0);
        saveSession(null, RoastingResult.PERFECT, 90, 200.0);

        stats()
            .andExpect(status().isOk())
            // (80 + 71 + 5 + 33 + 90) / 5 = 55.8
            .andExpect(jsonPath("$.averageScore").value(55.8))
            .andExpect(jsonPath("$.averageScoreByLevel.BEGINNER").value(75.5))
            .andExpect(jsonPath("$.averageScoreByLevel.INTERMEDIATE").isEmpty())
            .andExpect(jsonPath("$.averageScoreByLevel.ADVANCED").value(5.0))
            .andExpect(jsonPath("$.averageScoreByLevel.NOT_SET").value(61.5))
            // Los campos que ya existían siguen iguales.
            .andExpect(jsonPath("$.totalUsers").value(2))
            .andExpect(jsonPath("$.adminUsers").value(1))
            .andExpect(jsonPath("$.totalSessions").value(5))
            .andExpect(jsonPath("$.sessionResultCounts.PERFECT").value(3))
            .andExpect(jsonPath("$.knowledgeLevelCounts.NOT_SET").value(2));
    }

    @Test
    void shouldSendEmptyAveragesWhenThereAreNoSessions() throws Exception {
        stats()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.averageScore").isEmpty())
            .andExpect(jsonPath("$.averageScoreByLevel.BEGINNER").isEmpty())
            .andExpect(jsonPath("$.averageScoreByLevel.NOT_SET").isEmpty())
            .andExpect(jsonPath("$.totalSessions").value(0));
    }

    @Test
    void shouldAddLevelAndKeepTargetTemperatureInTheSessionList() throws Exception {
        saveSession(KnowledgeLevel.INTERMEDIATE, RoastingResult.PERFECT, 70, 210.0);
        saveSession(null, RoastingResult.RAW, 33, 180.0);

        mockMvc.perform(get("/api/v1/admin/roasting-sessions?sort=id").header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].knowledgeLevel").value("INTERMEDIATE"))
            .andExpect(jsonPath("$.content[0].targetTemperature").value(210.0))
            .andExpect(jsonPath("$.content[1].knowledgeLevel").isEmpty())
            .andExpect(jsonPath("$.content[1].targetTemperature").value(180.0))
            .andExpect(jsonPath("$.content[0].userUsername").value("playerUser"))
            .andExpect(jsonPath("$.content[0].qualityScore").value(70));
    }

    @Test
    void shouldKeepTheStatsOnlyForAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", bearer(player)))
            .andExpect(status().isForbidden());
    }

    private void saveSession(KnowledgeLevel level, RoastingResult result, int score, double targetTemperature) {
        roastingSessionRepository.save(new RoastingSession(
            player, 190.0, targetTemperature, 780, 205.0, 206.0, result, score, true, 150, level
        ));
    }

    private ResultActions stats() throws Exception {
        return mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", bearer(admin)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }

    private User saveUser(String username, Role role) {
        User user = new User("User", username.toLowerCase() + "@toastedvr.test", username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        return userRepository.save(Objects.requireNonNull(user));
    }
}
