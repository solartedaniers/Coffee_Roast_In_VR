package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
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

// La ruta del asistente de voz exige lo mismo que POST /api/v1/roasting/sessions
// (usuario autenticado) y las reglas de las demás rutas no cambian.
@SpringBootTest
@AutoConfigureMockMvc
class VoiceAssistantSecurityIntegrationTests {

    private static final String QUESTIONS = "/api/v1/roasting/assistant/questions";

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

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "voice@toastedvr.test", "voiceUser", passwordEncoder.encode("Password123!"));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.BEGINNER);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectTheVoiceQuestionWithoutToken() throws Exception {
        mockMvc.perform(post(QUESTIONS)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTheVoiceQuestionWithAnInvalidToken() throws Exception {
        mockMvc.perform(post(QUESTIONS).header("Authorization", "Bearer not-a-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetAnAuthenticatedPlayerReachTheVoiceQuestion() throws Exception {
        int status = mockMvc.perform(post(QUESTIONS).header("Authorization", bearer()))
            .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldOnlyOpenPostOnTheVoiceQuestion() throws Exception {
        mockMvc.perform(get(QUESTIONS).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roasting/assistant/other").header("Authorization", bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldKeepTheExistingRoastingRulesUnchanged() throws Exception {
        mockMvc.perform(post("/api/v1/roasting/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/ranking")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/sessions").header("Authorization", bearer())).andExpect(status().isOk());
    }

    @Test
    void shouldKeepAdminRoutesAndUnknownRoutesClosed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/unknown").header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(player));
    }
}
