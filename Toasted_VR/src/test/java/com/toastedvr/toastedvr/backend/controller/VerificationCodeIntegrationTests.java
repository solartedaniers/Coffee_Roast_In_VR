package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.OneTimeCode;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.OneTimeCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.service.EmailService;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VerificationCodeIntegrationTests {

    private static final String EMAIL = "pending@toastedvr.test";

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
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        oneTimeCodeRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnCodePolicyAndStoreOnlyTheCodeHash() throws Exception {
        register()
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.expiresInMinutes").value(1))
            .andExpect(jsonPath("$.codePolicy.expiresInSeconds").value(60))
            .andExpect(jsonPath("$.codePolicy.maxAttempts").value(5))
            .andExpect(jsonPath("$.codePolicy.maxResends").value(3))
            .andExpect(jsonPath("$.codePolicy.resendLockMinutes").value(5));

        String code = lastSentCode();
        List<OneTimeCode> codes = oneTimeCodeRepository.findAll();
        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).getCodeHash()).isNotEqualTo(code).hasSize(64);
        assertThat(codes.get(0).getExpiresAt()).isBefore(LocalDateTime.now().plusSeconds(61));
    }

    @Test
    void shouldVerifyAccountWithCorrectCodeAndConsumeIt() throws Exception {
        register();

        verifyCode(lastSentCode())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(oneTimeCodeRepository.findAll()).isEmpty();
    }

    @Test
    void shouldCountDownAttemptsAndInvalidateCodeAfterFiveFailures() throws Exception {
        register();
        String code = lastSentCode();
        String wrongCode = code.equals("111111") ? "222222" : "111111";

        for (int remaining = 4; remaining >= 1; remaining--) {
            verifyCode(wrongCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                .andExpect(jsonPath("$.details.remainingAttempts").value(remaining));
        }

        verifyCode(wrongCode)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE_ATTEMPTS_EXHAUSTED"))
            .andExpect(jsonPath("$.message").value("Agotaste los intentos para este código. Solicita uno nuevo."));

        verifyCode(code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE_ATTEMPTS_EXHAUSTED"));
    }

    @Test
    void shouldShowSingularMessageWhenOneAttemptIsLeft() throws Exception {
        register();
        String wrongCode = lastSentCode().equals("111111") ? "222222" : "111111";

        for (int attempt = 0; attempt < 3; attempt++) {
            verifyCode(wrongCode);
        }

        verifyCode(wrongCode)
            .andExpect(jsonPath("$.message").value("El código ingresado no es correcto. Te queda 1 intento."));
    }

    @Test
    void shouldRejectExpiredCodeWithoutSendingANewOneAutomatically() throws Exception {
        register();
        String code = lastSentCode();
        expireCodes();

        verifyCode(code)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE_EXPIRED"))
            .andExpect(jsonPath("$.message").value("El código venció. Solicita uno nuevo."));
        verify(emailService, times(1)).sendVerificationCode(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void shouldRejectResendWhileCodeIsStillActive() throws Exception {
        register();

        resend()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CODE_STILL_ACTIVE"))
            .andExpect(jsonPath("$.details.secondsRemaining").isNumber());
    }

    @Test
    void shouldResendNewCodeAfterExpirationAndResetAttempts() throws Exception {
        register();
        String oldCode = lastSentCode();
        verifyCode(oldCode.equals("111111") ? "222222" : "111111");
        expireCodes();

        resend()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codePolicy.expiresInSeconds").value(60));
        String newCode = lastSentCode();

        assertThat(oneTimeCodeRepository.findAll().get(0).getFailedAttempts()).isZero();
        if (!newCode.equals(oldCode)) {
            verifyCode(oldCode).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        verifyCode(newCode).andExpect(status().isOk());
    }

    @Test
    void shouldAllowImmediateResendAfterAttemptsAreExhausted() throws Exception {
        register();
        String wrongCode = lastSentCode().equals("111111") ? "222222" : "111111";
        for (int attempt = 0; attempt < 5; attempt++) {
            verifyCode(wrongCode);
        }

        resend().andExpect(status().isOk());
        verifyCode(lastSentCode()).andExpect(status().isOk());
    }

    @Test
    void shouldLockResendsForFiveMinutesAfterThreeResends() throws Exception {
        register();

        for (int resendNumber = 0; resendNumber < 3; resendNumber++) {
            expireCodes();
            resend().andExpect(status().isOk());
        }

        expireCodes();
        resend()
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOO_MANY_RESENDS"))
            .andExpect(jsonPath("$.message").value("Intenta más tarde."))
            .andExpect(jsonPath("$.details.secondsRemaining").value(greaterThan(290)));
        resend().andExpect(jsonPath("$.code").value("TOO_MANY_RESENDS"));

        jdbcTemplate.update("UPDATE one_time_codes SET resend_locked_until = ?", LocalDateTime.now().minusSeconds(1));
        resend().andExpect(status().isOk());
    }

    @Test
    void shouldNotSendAnythingWhenAccountIsAlreadyVerified() throws Exception {
        register();
        verifyCode(lastSentCode());

        resend()
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Esta cuenta ya fue verificada."));
        verify(emailService, times(1)).sendVerificationCode(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void shouldReturnNotFoundWhenResendingForUnknownEmail() throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/resend-verification-code")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", "nobody@toastedvr.test")))
            )
            .andExpect(status().isNotFound());
        verify(emailService, never()).sendVerificationCode(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectEmailWithoutDotInDomainOnEveryEndpoint() throws Exception {
        String invalidEmailMessage = "Debes ingresar un correo electrónico válido (por ejemplo, usuario@dominio.com).";

        registerWithEmail("a@gmailcom")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value(invalidEmailMessage));
        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", "a@gmailcom", "password", "Password123!")))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(invalidEmailMessage));
        mockMvc.perform(
                post("/api/v1/auth/resend-verification-code")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("email", "a@gmailcom")))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(invalidEmailMessage));

        assertThat(userRepository.findAll()).isEmpty();
        verify(emailService, never()).sendVerificationCode(anyString(), anyString(), anyString());
    }

    @Test
    void shouldAcceptEmailWithDomainAndExtension() throws Exception {
        registerWithEmail("a@gmail.com").andExpect(status().isCreated());
    }

    private ResultActions registerWithEmail(String email) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of(
                    "name", "Mail User",
                    "email", email,
                    "username", "mailUser",
                    "password", "Password123!"
                )))
        );
    }

    private ResultActions register() throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of(
                    "name", "Pending User",
                    "email", EMAIL,
                    "username", "pendingUser",
                    "password", "Password123!"
                )))
        );
    }

    private ResultActions verifyCode(String code) throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("email", EMAIL, "code", code)))
        );
    }

    private ResultActions resend() throws Exception {
        return mockMvc.perform(
            post("/api/v1/auth/resend-verification-code")
                .contentType(jsonMediaType())
                .content(requireJson(Map.of("email", EMAIL)))
        );
    }

    private String lastSentCode() {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
            .sendVerificationCode(eq(EMAIL), anyString(), codeCaptor.capture());
        return codeCaptor.getValue();
    }

    private void expireCodes() {
        jdbcTemplate.update("UPDATE one_time_codes SET expires_at = ?", LocalDateTime.now().minusSeconds(1));
    }

    private @NonNull MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON, "Media type must not be null.");
    }

    private @NonNull String requireJson(Object value) throws Exception {
        return Objects.requireNonNull(objectMapper.writeValueAsString(value), "Serialized JSON must not be null.");
    }
}
