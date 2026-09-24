package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.util.HashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordAndUsernamePolicyIntegrationTests {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_POLICY_MESSAGE =
        "La contraseña debe tener mínimo 8 caracteres, una mayúscula, una minúscula y un número.";
    private static final String USERNAME_LENGTH_MESSAGE = "El usuario debe tener entre 4 y 20 caracteres.";

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

    @BeforeEach
    void setUp() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectRegistrationWithWeakPassword() throws Exception {
        register("newPlayer", "password123")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(PASSWORD_POLICY_MESSAGE));
    }

    @Test
    void shouldRejectRegistrationWithUsernameOutsideLimits() throws Exception {
        register("abc", PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(USERNAME_LENGTH_MESSAGE));
        register("a".repeat(21), PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(USERNAME_LENGTH_MESSAGE));
    }

    @Test
    void shouldRejectWeakNewPasswordInProfile() throws Exception {
        User user = saveUser("player1");

        updateProfile(user, "player1", PASSWORD, "weakpass")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(PASSWORD_POLICY_MESSAGE));
        assertThat(passwordEncoder.matches(PASSWORD, reload(user).getPassword())).isTrue();
    }

    @Test
    void shouldChangePasswordInProfileWhenPolicyIsMet() throws Exception {
        User user = saveUser("player1");

        updateProfile(user, "player1", PASSWORD, "NewPassword9").andExpect(status().isOk());
        assertThat(passwordEncoder.matches("NewPassword9", reload(user).getPassword())).isTrue();
    }

    @Test
    void shouldRejectChangingUsernameToOneOutsideLimits() throws Exception {
        User user = saveUser("player1");

        updateProfile(user, "abc", null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(USERNAME_LENGTH_MESSAGE));
        updateProfile(user, "a".repeat(21), null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(USERNAME_LENGTH_MESSAGE));
        assertThat(reload(user).getUsername()).isEqualTo("player1");
    }

    @Test
    void shouldKeepSavingProfileWhenLegacyShortUsernameIsUnchanged() throws Exception {
        // Usuario creado antes del límite de 4 a 20 caracteres.
        User legacyUser = saveUser("ana");

        updateProfile(legacyUser, "ana", null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("ana"));
    }

    @Test
    void shouldAllowLegacyUserToMoveToValidUsername() throws Exception {
        User legacyUser = saveUser("ana");

        updateProfile(legacyUser, "anaTorres", null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("anaTorres"));
    }

    private ResultActions register(String username, String password) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of(
                    "name", "New Player",
                    "email", "new@toastedvr.test",
                    "username", username,
                    "password", password
                )))
        );
    }

    private ResultActions updateProfile(User user, String username, String currentPassword, String newPassword)
        throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", user.getName());
        body.put("username", username);
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);

        return mockMvc.perform(
            patch("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + jwtService.generateToken(new UserPrincipal(user)))
                .contentType(jsonMediaType())
                .content(requireJson(body))
        );
    }

    private User saveUser(String username) {
        User user = new User("Player", username + "@toastedvr.test", username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(Objects.requireNonNull(user.getId())).orElseThrow();
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
