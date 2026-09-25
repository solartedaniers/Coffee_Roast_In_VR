package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTests {

    private static final String EMAIL = "refresh@toastedvr.test";
    private static final String PASSWORD = "Password123!";

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
    private TokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User("Refresh User", EMAIL, "refreshUser", passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        userRepository.save(user);
    }

    @Test
    void shouldIssueTenMinuteAccessToken() throws Exception {
        Instant expiresAt = Instant.parse(login().get("expiresAt").asText());

        assertThat(Duration.between(Instant.now(), expiresAt).getSeconds()).isBetween(590L, 600L);
    }

    @Test
    void shouldStoreOnlyTheRefreshTokenHashWithOneDayExpiration() throws Exception {
        String refreshToken = login().get("refreshToken").asText();
        User user = findUser();

        assertThat(user.getRefreshTokenHash()).isNotEqualTo(refreshToken);
        assertThat(user.getRefreshTokenHash()).isEqualTo(tokenHasher.hash(refreshToken));
        // Un segundo de margen arriba: la base redondea a microsegundos y en
        // Windows el reloj puede dar el mismo instante al servicio y al test.
        assertThat(user.getRefreshTokenExpiresAt())
            .isBetween(LocalDateTime.now().plusHours(23).plusMinutes(59), LocalDateTime.now().plusHours(24).plusSeconds(1));
    }

    @Test
    void shouldRotateRefreshTokenOnEachUse() throws Exception {
        String firstToken = login().get("refreshToken").asText();

        String secondToken = objectMapper.readTree(
                refresh(firstToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString()
            )
            .get("refreshToken")
            .asText();

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(findUser().getRefreshTokenHash()).isEqualTo(tokenHasher.hash(secondToken));

        refresh(firstToken)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("El refresh token no es válido."));
        refresh(secondToken).andExpect(status().isOk());
    }

    @Test
    void shouldRejectExpiredRefreshTokenAndClearIt() throws Exception {
        String refreshToken = login().get("refreshToken").asText();
        User user = findUser();
        user.updateRefreshTokenHash(user.getRefreshTokenHash(), LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);

        refresh(refreshToken)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("El refresh token expiró. Por favor inicia sesión nuevamente."));
        assertThat(findUser().getRefreshTokenHash()).isNull();
    }

    @Test
    void shouldRejectLegacyPlainTextRefreshToken() throws Exception {
        // Simula un refresh token guardado en texto plano antes de este cambio.
        String legacyToken = "3f2b8c1e-0000-4000-8000-000000000000";
        User user = findUser();
        user.updateRefreshTokenHash(legacyToken, LocalDateTime.now().plusDays(10));
        userRepository.save(user);

        refresh(legacyToken).andExpect(status().isUnauthorized());
    }

    private JsonNode login() throws Exception {
        String response = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", EMAIL, "password", PASSWORD)))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("refreshToken", refreshToken)))
        );
    }

    private User findUser() {
        return userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
