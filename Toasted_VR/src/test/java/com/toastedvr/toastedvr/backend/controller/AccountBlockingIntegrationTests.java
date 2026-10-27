package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountBlockingIntegrationTests {

    private static final String PLAYER_EMAIL = "player@toastedvr.test";
    private static final String PASSWORD = "Password123!";
    private static final String BLOCKED_MESSAGE = "Fuiste bloqueado por el administrador.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private String adminToken;
    private Long playerId;

    @BeforeEach
    void setUp() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();

        User admin = saveUser("Admin", "admin@toastedvr.test", "adminUser", Role.ADMIN);
        User player = saveUser("Player", PLAYER_EMAIL, "playerUser", Role.PLAYER);

        adminToken = jwtService.generateToken(new UserPrincipal(admin));
        playerId = player.getId();
    }

    @Test
    void shouldRejectNextRequestWithValidAccessTokenAfterBlocking() throws Exception {
        String playerAccessToken = login().get("accessToken").asText();
        updateKnowledgeLevel(playerAccessToken).andExpect(status().isOk());

        updateStatus(false).andExpect(status().isOk());

        updateKnowledgeLevel(playerAccessToken)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
            .andExpect(jsonPath("$.message").value(BLOCKED_MESSAGE));
    }

    @Test
    void shouldReportBlockEvenWhenAccessTokenAlreadyExpired() throws Exception {
        User player = userRepository.findById(requirePlayerId()).orElseThrow();
        String expiredToken = new JwtService(jwtSecret, -60).generateToken(new UserPrincipal(player));

        updateStatus(false).andExpect(status().isOk());

        updateKnowledgeLevel(expiredToken)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void shouldClearRefreshTokenWhenBlocking() throws Exception {
        String refreshToken = login().get("refreshToken").asText();

        updateStatus(false).andExpect(status().isOk());

        assertThat(userRepository.findById(requirePlayerId()).orElseThrow().getRefreshTokenHash()).isNull();
        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("refreshToken", refreshToken)))
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldShowBlockedMessageOnEveryLoginAttempt() throws Exception {
        updateStatus(false).andExpect(status().isOk());

        for (int attempt = 0; attempt < 2; attempt++) {
            loginRequest()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.message").value(BLOCKED_MESSAGE));
        }
    }

    @Test
    void shouldAllowLoginAgainAfterReactivation() throws Exception {
        updateStatus(false).andExpect(status().isOk());
        updateStatus(true).andExpect(status().isOk());

        String playerAccessToken = login().get("accessToken").asText();
        updateKnowledgeLevel(playerAccessToken).andExpect(status().isOk());
    }

    @Test
    void shouldKeepGenericUnauthorizedResponseForInvalidTokens() throws Exception {
        updateKnowledgeLevel("not-a-jwt")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private JsonNode login() throws Exception {
        String response = loginRequest()
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response);
    }

    private ResultActions loginRequest() throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("email", PLAYER_EMAIL, "password", PASSWORD)))
        );
    }

    private ResultActions updateKnowledgeLevel(String accessToken) throws Exception {
        return mockMvc.perform(
            patch("/api/v1/users/me/knowledge-level")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(jsonMediaType())
                .content("{\"knowledgeLevel\":\"BEGINNER\"}")
        );
    }

    private ResultActions updateStatus(boolean enabled) throws Exception {
        return mockMvc.perform(
            patch("/api/v1/admin/users/{id}/status", requirePlayerId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(jsonMediaType())
                .content("{\"enabled\":" + enabled + "}")
        );
    }

    private User saveUser(String name, String email, String username, Role role) {
        User user = new User(name, email, username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        return userRepository.save(user);
    }

    private @NonNull Long requirePlayerId() {
        return Objects.requireNonNull(playerId, "Player ID must not be null.");
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
