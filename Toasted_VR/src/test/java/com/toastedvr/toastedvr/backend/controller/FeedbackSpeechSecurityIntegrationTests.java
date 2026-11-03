package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

// La lectura en voz alta de la retroalimentación exige un usuario autenticado, solo por POST, y las demás reglas no cambian.
@SpringBootTest
@SuppressWarnings("null")
@AutoConfigureMockMvc
class FeedbackSpeechSecurityIntegrationTests {

    private static final String FEEDBACK = "/api/v1/roasting/sessions/42/feedback";
    private static final String SPEECH = FEEDBACK + "/speech";

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
        player = new User("Player", "speech@toastedvr.test", "speechUser", passwordEncoder.encode("Password123!"));
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
    void shouldRejectTheSpeechWithoutToken() throws Exception {
        mockMvc.perform(post(SPEECH)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTheSpeechWithAnInvalidToken() throws Exception {
        mockMvc.perform(post(SPEECH).header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
    }

    // Sin la regla, denyAll respondería 403 aun con token.
    @Test
    void shouldLetAnAuthenticatedPlayerThrough() throws Exception {
        mockMvc.perform(post(SPEECH).header("Authorization", bearer()))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
    }

    @Test
    void shouldOnlyOpenThePostMethod() throws Exception {
        mockMvc.perform(get(SPEECH).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(delete(SPEECH).header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    @Test
    void shouldKeepTheExistingRulesUnchanged() throws Exception {
        mockMvc.perform(get(FEEDBACK)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(FEEDBACK).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roasting/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/sessions").header("Authorization", bearer())).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/roasting/simulations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/roasting/assistant/questions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/roasting/sessions/42/speech").header("Authorization", bearer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/unknown").header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(player));
    }
}
