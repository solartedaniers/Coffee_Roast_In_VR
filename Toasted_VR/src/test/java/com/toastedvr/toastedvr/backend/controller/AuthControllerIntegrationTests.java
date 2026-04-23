package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTests {

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

    @BeforeEach
    void setUp() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldLoginVerifiedAndEnabledUser() throws Exception {
        createUser("Admin User", "admin@toastedvr.test", "adminUser", true, true, Role.ADMIN);

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("identifier", "adminUser", "password", "Password123!")))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").isNumber())
            .andExpect(jsonPath("$.user.username").value("adminUser"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.user.lastLoginAt").isNotEmpty());
    }

    @Test
    void shouldRejectLoginWhenEmailIsNotVerified() throws Exception {
        createUser("Pending User", "pending@toastedvr.test", "pendingUser", false, true, Role.PLAYER);

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("identifier", "pendingUser", "password", "Password123!")))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Debes verificar tu correo antes de iniciar sesion."));
    }

    @Test
    void shouldRejectLoginWhenAccountIsBlocked() throws Exception {
        createUser("Blocked User", "blocked@toastedvr.test", "blockedUser", true, false, Role.PLAYER);

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("identifier", "blockedUser", "password", "Password123!")))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("La cuenta se encuentra bloqueada."));
    }

    @Test
    void shouldBlacklistTokenAfterLogout() throws Exception {
        createUser("Admin User", "admin@toastedvr.test", "adminUser", true, true, Role.ADMIN);

        String loginResponse = mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("identifier", "admin@toastedvr.test", "password", "Password123!")))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String accessToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header("Authorization", "Bearer " + accessToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("La sesion fue cerrada correctamente."));

        mockMvc.perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + accessToken)
            )
            .andExpect(status().isUnauthorized());
    }

    private void createUser(
        String name,
        String email,
        String username,
        boolean emailVerified,
        boolean enabled,
        Role role
    ) {
        User user = new User(name, email, username, passwordEncoder.encode("Password123!"));

        if (emailVerified) {
            user.markEmailAsVerified();
        }

        if (!enabled) {
            user.block();
        }

        user.assignRole(role);
        userRepository.save(user);
    }

    private String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(asJson(value), "Serialized JSON must not be null.");
    }
}
