package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private Long adminId;
    private Long playerId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User admin = createUser("Alexander", "alexander@toastedvr.test", "Alexander", Role.ADMIN, true);
        User player = createUser("Danier", "danier@toastedvr.test", "Danier", Role.PLAYER, true);
        createUser("Omaira", "omaira@toastedvr.test", "Omaira", Role.PLAYER, false);

        adminId = Objects.requireNonNull(admin.getId(), "Saved administrator ID must not be null.");
        playerId = Objects.requireNonNull(player.getId(), "Saved player ID must not be null.");
        adminToken = jwtService.generateToken(new UserPrincipal(admin));
    }

    @Test
    void shouldListUsersWithoutOptionalFilters() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void shouldReturnSelectedUserDetails() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users/{id}", requirePlayerId())
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(requirePlayerId()))
            .andExpect(jsonPath("$.username").value("Danier"))
            .andExpect(jsonPath("$.emailVerified").value(true))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @SuppressWarnings("null")
    void shouldBlockAndActivateUserWithPersistedStatus() throws Exception {
        updateStatus(requirePlayerId(), false)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.message").value("La cuenta fue bloqueada correctamente."));
        assertThat(userRepository.findById(requirePlayerId())).isPresent()
            .get()
            .extracting(User::isEnabled)
            .isEqualTo(false);

        updateStatus(requirePlayerId(), true)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.message").value("La cuenta fue activada correctamente."));
        assertThat(userRepository.findById(requirePlayerId())).isPresent()
            .get()
            .extracting(User::isEnabled)
            .isEqualTo(true);
    }

    @Test
    @SuppressWarnings("null")
    void shouldPreventAdministratorFromBlockingOwnAccount() throws Exception {
        mockMvc.perform(
                patch("/api/v1/admin/users/{id}/status", requireAdminId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(jsonMediaType())
                    .content("{\"enabled\":false}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("No puedes bloquear tu propia cuenta."))
            .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(userRepository.findById(requireAdminId())).isPresent()
            .get()
            .extracting(User::isEnabled)
            .isEqualTo(true);
    }

    @Test
    void shouldReturnSpanishNotFoundMessageWithErrorCode() throws Exception {
        mockMvc.perform(
                get("/api/v1/admin/users/{id}", Long.MAX_VALUE)
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Usuario no encontrado."))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturnSpanishValidationMessageWhenStatusIsMissing() throws Exception {
        mockMvc.perform(
                patch("/api/v1/admin/users/{id}/status", requirePlayerId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(jsonMediaType())
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El campo enabled es obligatorio."))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions updateStatus(
        @NonNull Long userId,
        boolean enabled
    ) throws Exception {
        return mockMvc.perform(
                patch("/api/v1/admin/users/{id}/status", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(jsonMediaType())
                    .content("{\"enabled\":" + enabled + "}")
            )
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(jsonMediaType()));
    }

    private @NonNull Long requireAdminId() {
        return Objects.requireNonNull(adminId, "Administrator ID must not be null.");
    }

    private @NonNull Long requirePlayerId() {
        return Objects.requireNonNull(playerId, "Player ID must not be null.");
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private User createUser(String name, String email, String username, Role role, boolean enabled) {
        User user = new User(name, email, username, passwordEncoder.encode("Password123!"));
        user.markEmailAsVerified();
        user.assignRole(role);

        if (enabled) {
            user.activate();
        } else {
            user.block();
        }

        return userRepository.save(user);
    }
}
