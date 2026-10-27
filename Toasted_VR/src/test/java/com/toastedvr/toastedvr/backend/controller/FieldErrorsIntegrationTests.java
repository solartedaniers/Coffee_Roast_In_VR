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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FieldErrorsIntegrationTests {

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
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnEveryValidationErrorByFieldAndKeepGeneralMessage() throws Exception {
        register("", "a@gmailcom", "abc", "weak")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andExpect(jsonPath("$.details.fieldErrors.name").value("El nombre es obligatorio."))
            .andExpect(jsonPath("$.details.fieldErrors.email")
                .value("Debes ingresar un correo electrónico válido (por ejemplo, usuario@dominio.com)."))
            .andExpect(jsonPath("$.details.fieldErrors.username").value("El usuario debe tener entre 4 y 20 caracteres."))
            .andExpect(jsonPath("$.details.fieldErrors.password")
                .value("La contraseña debe tener entre 8 y 72 caracteres, una mayúscula, una minúscula y un número."));
    }

    @Test
    void shouldPointDuplicateEmailToEmailField() throws Exception {
        saveUser("existing", "taken@toastedvr.test");

        register("New Player", "taken@toastedvr.test", "newPlayer", PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("El correo electrónico ya está registrado."))
            .andExpect(jsonPath("$.details.fieldErrors.email").value("El correo electrónico ya está registrado."));
    }

    @Test
    void shouldPointDuplicateUsernameToUsernameField() throws Exception {
        saveUser("takenName", "other@toastedvr.test");

        register("New Player", "new@toastedvr.test", "takenName", PASSWORD)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.fieldErrors.username").value("El nombre de usuario ya está en uso."));
    }

    @Test
    void shouldPointProfileErrorsToTheirFields() throws Exception {
        User user = saveUser("player1", "player1@toastedvr.test");
        saveUser("takenName", "other@toastedvr.test");

        updateProfile(user, "takenName", null, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.fieldErrors.username").value("El nombre de usuario ya está en uso."));
        updateProfile(user, "abc", null, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.username").value("El usuario debe tener entre 4 y 20 caracteres."));
        updateProfile(user, "player1", "WrongPassword1", "NewPassword9")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.details.fieldErrors.currentPassword").value("La contraseña actual no es correcta."));
        updateProfile(user, "player1", PASSWORD, "weak")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.newPassword").isNotEmpty());
    }

    @Test
    void shouldReportMissingCurrentPasswordAsBadRequestOnItsField() throws Exception {
        User user = saveUser("player1", "player1@toastedvr.test");

        updateProfile(user, "player1", null, "NewPassword9")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.currentPassword").value("Debes ingresar tu contraseña actual."));
    }

    @Test
    void shouldValidateVerifyEmailRequestWithSharedRules() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/verify-email")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", "a@gmailcom", "code", "12ab")))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.email")
                .value("Debes ingresar un correo electrónico válido (por ejemplo, usuario@dominio.com)."))
            .andExpect(jsonPath("$.details.fieldErrors.code").value("El código debe tener 6 dígitos."));
    }

    @Test
    void shouldKeepProfileNameLimitMessageFromMessagesFile() throws Exception {
        User user = saveUser("player1", "player1@toastedvr.test");
        Map<String, Object> body = new HashMap<>();
        body.put("name", "a".repeat(121));
        body.put("username", "player1");

        mockMvc.perform(
                patch("/api/v1/users/me/profile")
                    .header("Authorization", "Bearer " + jwtService.generateToken(new UserPrincipal(user)))
                    .contentType(jsonMediaType())
                    .content(requireJson(body))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.name").value("El nombre no puede superar 120 caracteres."));
    }

    @Test
    void shouldNotAddFieldErrorsToErrorsThatBelongToNoField() throws Exception {
        saveUser("player1", "player1@toastedvr.test");

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", "player1@toastedvr.test", "password", "WrongPassword1")))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    private ResultActions register(String name, String email, String username, String password) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("name", name, "email", email, "username", username, "password", password)))
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

    private User saveUser(String username, String email) {
        User user = new User("Player", email, username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        return userRepository.save(user);
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
