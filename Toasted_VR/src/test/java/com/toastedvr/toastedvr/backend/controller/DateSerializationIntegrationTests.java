package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Las fechas de la API salen como Instant en UTC (terminan en "Z") y
// representan el mismo momento que se guardó en la entidad.
@SpringBootTest
@AutoConfigureMockMvc
class DateSerializationIntegrationTests {

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

    private User admin;
    private User player;

    @BeforeEach
    void setUp() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
        admin = saveUser("adminUser", "admin@toastedvr.test", Role.ADMIN);
        player = saveUser("playerUser", "player@toastedvr.test", Role.PLAYER);
    }

    @Test
    void shouldRunTheTestJvmInUtc() {
        assertThat(ZoneId.systemDefault().normalized()).isEqualTo(ZoneId.of("UTC").normalized());
    }

    @Test
    void shouldSendLastLoginAtAsUtcInstantInTheLoginResponse() throws Exception {
        JsonNode body = readJson(
            mockMvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(jsonMediaType())
                        .content(requireJson(Map.of("email", "player@toastedvr.test", "password", PASSWORD)))
                )
                .andExpect(status().isOk())
        );

        String lastLoginAt = body.at("/user/lastLoginAt").asText();
        assertThat(lastLoginAt).matches(UTC_INSTANT_PATTERN);
        assertThat(Duration.between(Instant.parse(lastLoginAt), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void shouldSendUserCreatedAtAsUtcInstantInAdminListAndDetail() throws Exception {
        Instant storedCreatedAt = userRepository.findById(requireId(player)).orElseThrow()
            .getCreatedAt().atZone(ZoneId.systemDefault()).toInstant();

        JsonNode list = readJson(adminGet("/api/v1/admin/users").andExpect(status().isOk()));
        list.get("content").forEach(user -> assertThat(user.get("createdAt").asText()).matches(UTC_INSTANT_PATTERN));

        String detailCreatedAt = readJson(adminGet("/api/v1/admin/users/" + player.getId()).andExpect(status().isOk()))
            .get("createdAt").asText();
        assertThat(detailCreatedAt).matches(UTC_INSTANT_PATTERN);
        assertThat(Instant.parse(detailCreatedAt)).isEqualTo(storedCreatedAt);
    }

    @Test
    void shouldSendRoastingSessionCreatedAtAsUtcInstant() throws Exception {
        String savedCreatedAt = readJson(
            mockMvc.perform(
                    post("/api/v1/roasting/sessions")
                        .header("Authorization", "Bearer " + tokenFor(player))
                        .contentType(jsonMediaType())
                        .content(requireJson(Map.of(
                            "chargeTemperature", 190.0,
                            "targetTemperature", 200.0,
                            "totalDurationSeconds", 780,
                            "finalTemperature", 200.5,
                            "peakTemperature", 201.0,
                            "result", "PERFECT",
                            "qualityScore", 90,
                            "firstCrackReached", true,
                            "developmentTimeSeconds", 150
                        )))
                )
                .andExpect(status().isCreated())
        ).get("createdAt").asText();
        assertThat(savedCreatedAt).matches(UTC_INSTANT_PATTERN);

        JsonNode adminSessions = readJson(
            adminGet("/api/v1/admin/roasting-sessions").andExpect(status().isOk())
        );
        assertThat(adminSessions.at("/content/0/createdAt").asText()).matches(UTC_INSTANT_PATTERN);
    }

    private ResultActions adminGet(String path) throws Exception {
        return mockMvc.perform(get(Objects.requireNonNull(path)).header("Authorization", "Bearer " + tokenFor(admin)));
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(new UserPrincipal(user));
    }

    private User saveUser(String username, String email, Role role) {
        User user = new User("User", email, username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        return userRepository.save(user);
    }

    private JsonNode readJson(ResultActions resultActions) throws Exception {
        return objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
    }

    private @NonNull Long requireId(User user) {
        return Objects.requireNonNull(user.getId(), "User ID must not be null.");
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
