package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.OneTimeCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.service.AuditService;
import com.toastedvr.toastedvr.backend.service.EmailService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTests {

    private static final String EMAIL = "player@toastedvr.test";
    private static final String OLD_PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPassword9";
    private static final String GENERIC_REQUEST_MESSAGE = "Si el correo está registrado, recibirás un código.";
    private static final String GENERIC_CODE_ERROR = "El código no es válido o venció. Solicita uno nuevo.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneTimeCodeRepository oneTimeCodeRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    @MockitoSpyBean
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        oneTimeCodeRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
        saveUser("player", EMAIL, true, true);
    }

    @Test
    void shouldAnswerTheSameAndSendNothingForUnknownBlockedOrUnverifiedEmails() throws Exception {
        saveUser("blocked", "blocked@toastedvr.test", true, false);
        saveUser("pending", "pending@toastedvr.test", false, true);

        for (String email : new String[] {"nobody@toastedvr.test", "blocked@toastedvr.test", "pending@toastedvr.test"}) {
            requestCode(email)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_REQUEST_MESSAGE))
                .andExpect(jsonPath("$.codePolicy.expiresInSeconds").value(60));
        }

        verify(emailService, never()).sendPasswordResetCode(anyString(), anyString(), anyString());
        assertThat(oneTimeCodeRepository.findAll()).isEmpty();
    }

    @Test
    void shouldSendCodeWithSameGenericAnswerForEligibleAccount() throws Exception {
        requestCode(EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_REQUEST_MESSAGE));

        String code = lastSentCode();
        assertThat(code).matches("\\d{6}");
        assertThat(oneTimeCodeRepository.findAll().get(0).getCodeHash()).isNotEqualTo(code);
    }

    @Test
    void shouldChangePasswordCloseSessionsAndAudit() throws Exception {
        JsonNode session = login(OLD_PASSWORD).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString().transform(this::readJson);
        String oldAccessToken = session.get("accessToken").asText();
        String oldRefreshToken = session.get("refreshToken").asText();
        waitForNextSecond();

        requestCode(EMAIL);
        confirm(lastSentCode(), NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Tu contraseña fue actualizada. Inicia sesión con la nueva contraseña."));

        login(OLD_PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
        refresh(oldRefreshToken).andExpect(status().isUnauthorized());
        updateKnowledgeLevel(oldAccessToken)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REVOKED"))
            .andExpect(jsonPath("$.message").value("Tu sesión se cerró porque la contraseña cambió. Inicia sesión de nuevo."));
        verify(auditService, times(1)).logPasswordReset(anyLong());
        assertThat(oneTimeCodeRepository.findAll()).isEmpty();
    }

    @Test
    void shouldKeepTokensIssuedInTheSameSecondAsTheInvalidationValid() throws Exception {
        User user = findUser();
        String token = jwtService.generateToken(new com.toastedvr.toastedvr.backend.security.UserPrincipal(user));
        Instant issuedAt = jwtService.getIssuedAt(token);

        user.invalidateSessions(issuedAt.plusMillis(900));
        userRepository.save(user);
        assertThat(findUser().getSessionsInvalidatedAt()).isEqualTo(issuedAt);
        updateKnowledgeLevel(token).andExpect(status().isOk());

        user.invalidateSessions(issuedAt.plusSeconds(1));
        userRepository.save(user);
        updateKnowledgeLevel(token)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    void shouldInvalidateCodeAfterFiveWrongAttemptsWithTheSameGenericError() throws Exception {
        requestCode(EMAIL);
        String code = lastSentCode();
        String wrongCode = code.equals("111111") ? "222222" : "111111";

        for (int attempt = 0; attempt < 5; attempt++) {
            confirm(wrongCode, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                .andExpect(jsonPath("$.message").value(GENERIC_CODE_ERROR))
                .andExpect(jsonPath("$.details").doesNotExist());
        }

        confirm(code, NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(GENERIC_CODE_ERROR));
        login(OLD_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldRejectExpiredCodeAndUnknownEmailWithTheSameError() throws Exception {
        requestCode(EMAIL);
        String code = lastSentCode();
        jdbcTemplate.update("UPDATE one_time_codes SET expires_at = ?", LocalDateTime.now().minusSeconds(1));

        confirm(code, NEW_PASSWORD, NEW_PASSWORD).andExpect(jsonPath("$.message").value(GENERIC_CODE_ERROR));
        confirmFor("nobody@toastedvr.test", "123456", NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(GENERIC_CODE_ERROR));
    }

    @Test
    void shouldReportPasswordProblemsUnderTheirFields() throws Exception {
        requestCode(EMAIL);
        String code = lastSentCode();

        confirm(code, "weakpass", "weakpass")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.newPassword")
                .value("La contraseña debe tener entre 8 y 72 caracteres, una mayúscula, una minúscula y un número."));
        confirm(code, NEW_PASSWORD, "OtherPassword9")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors.confirmPassword").value("Las contraseñas no coinciden."));
        confirmFor("a@gmailcom", code, NEW_PASSWORD, NEW_PASSWORD)
            .andExpect(jsonPath("$.details.fieldErrors.email").isNotEmpty());

        confirm(code, NEW_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void shouldApplyResendLimitsSilently() throws Exception {
        requestCode(EMAIL);
        requestCode(EMAIL).andExpect(jsonPath("$.message").value(GENERIC_REQUEST_MESSAGE));
        verify(emailService, times(1)).sendPasswordResetCode(eq(EMAIL), anyString(), anyString());

        for (int resend = 0; resend < 3; resend++) {
            expireCodes();
            requestCode(EMAIL).andExpect(status().isOk());
        }
        verify(emailService, times(4)).sendPasswordResetCode(eq(EMAIL), anyString(), anyString());

        expireCodes();
        requestCode(EMAIL)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_REQUEST_MESSAGE));
        verify(emailService, times(4)).sendPasswordResetCode(eq(EMAIL), anyString(), anyString());
        assertThat(oneTimeCodeRepository.findAll().get(0).getResendLockedUntil()).isAfter(LocalDateTime.now());
    }

    private ResultActions requestCode(String email) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("email", email)))
        );
    }

    private ResultActions confirm(String code, String newPassword, String confirmPassword) throws Exception {
        return confirmFor(EMAIL, code, newPassword, confirmPassword);
    }

    private ResultActions confirmFor(String email, String code, String newPassword, String confirmPassword)
        throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of(
                    "email", email,
                    "code", code,
                    "newPassword", newPassword,
                    "confirmPassword", confirmPassword
                )))
        );
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("email", EMAIL, "password", password)))
        );
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("refreshToken", refreshToken)))
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

    private String lastSentCode() {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordResetCode(eq(EMAIL), anyString(), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    private void expireCodes() {
        jdbcTemplate.update("UPDATE one_time_codes SET expires_at = ?", LocalDateTime.now().minusSeconds(1));
    }

    // El iat del JWT tiene precisión de segundos: se espera al segundo siguiente
    // para que el token previo quede claramente antes de la invalidación.
    private void waitForNextSecond() throws InterruptedException {
        Thread.sleep(1100);
    }

    private User findUser() {
        return userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    private void saveUser(String username, String email, boolean verified, boolean enabled) {
        User user = new User("Player", email, username + "User", passwordEncoder.encode(OLD_PASSWORD));
        if (verified) {
            user.markEmailAsVerified();
        }
        if (!enabled) {
            user.block();
        }
        userRepository.save(user);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
