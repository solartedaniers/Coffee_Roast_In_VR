package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.UnityAccessCode;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UnityAccessCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.mail.enabled=true")
@AutoConfigureMockMvc
class UnityAccessCodeControllerIntegrationTests {

    private static final String CORRECT_PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UnityAccessCodeRepository unityAccessCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        unityAccessCodeRepository.deleteAll();
        userRepository.deleteAll();
        user = createUser("Unity User", "unity@toastedvr.test", "unityUser", true, true);
        accessToken = jwtService.generateToken(new UserPrincipal(user));
    }

    @Test
    void shouldCreateEncryptedCodeForAuthenticatedUserWithCorrectPassword() throws Exception {
        String code = createCode(CORRECT_PASSWORD);

        assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}");
        UnityAccessCode storedCode = unityAccessCodeRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(storedCode.getLookupHash()).doesNotContain(code.replace("-", ""));
        assertThat(storedCode.getEncryptedCode()).doesNotContain(code.replace("-", ""));
        assertThat(storedCode.getEncryptionIv()).isNotBlank();
        assertThat(storedCode.getCreatedAt()).isNotNull();
        assertThat(storedCode.getExpiresAt()).isAfter(storedCode.getCreatedAt());
        assertThat(storedCode.getExpiresAt()).isBeforeOrEqualTo(storedCode.getCreatedAt().plusMinutes(10));
    }

    @Test
    void shouldRejectCreationWithIncorrectPassword() throws Exception {
        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code", "WrongPassword!"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Current password is incorrect."));

        assertThat(unityAccessCodeRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void shouldAllowOnlyOneCodePerUser() throws Exception {
        createCode(CORRECT_PASSWORD);

        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code", CORRECT_PASSWORD))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("A Unity access code already exists for this user."));
    }

    @Test
    void shouldReturnStatusWithoutRawCode() throws Exception {
        createCode(CORRECT_PASSWORD);

        mockMvc.perform(
                get("/api/v1/users/me/unity-access-code")
                    .header("Authorization", "Bearer " + accessToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.expiresAt").exists())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.encryptedCode").doesNotExist())
            .andExpect(jsonPath("$.lookupHash").doesNotExist());
    }

    @Test
    void shouldRequireCorrectPasswordForRevealEmailAndRegeneration() throws Exception {
        createCode(CORRECT_PASSWORD);

        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/reveal", "WrongPassword!"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/email", "WrongPassword!"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/regenerate", "WrongPassword!"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRevealEmailAndRegenerateCodeAfterPasswordConfirmation() throws Exception {
        String originalCode = createCode(CORRECT_PASSWORD);
        LocalDateTime originalExpiration = storedCode().getExpiresAt();
        String revealedCode = responseField(
            mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/reveal", CORRECT_PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "code"
        );

        assertThat(revealedCode).isEqualTo(originalCode);
        assertThat(storedCode().getExpiresAt()).isEqualTo(originalExpiration);

        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/email", CORRECT_PASSWORD))
            .andExpect(status().isNoContent());
        verify(mailSender).send(anySimpleMailMessage());
        assertThat(storedCode().getLastEmailedAt()).isNotNull();
        assertThat(storedCode().getExpiresAt()).isEqualTo(originalExpiration);

        String regeneratedCode = responseField(
            mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/regenerate", CORRECT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regeneratedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "code"
        );

        assertThat(regeneratedCode).isNotEqualTo(originalCode);
        assertThat(storedCode().getExpiresAt()).isAfter(originalExpiration);
        assertUnityLoginRejected(originalCode);
        assertUnityLoginAccepted(regeneratedCode.toLowerCase().replace("-", " "));
    }

    @Test
    void shouldReturnLoginResponseForValidUnityCode() throws Exception {
        String code = createCode(CORRECT_PASSWORD);

        assertUnityLoginAccepted(code);
    }

    @Test
    void shouldRejectUnityLoginAfterCodeExpirationWithGenericMessage() throws Exception {
        String code = createCode(CORRECT_PASSWORD);
        updateStoredExpiration(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        assertUnityLoginRejected(code);
    }

    @Test
    void shouldRejectLegacyCodeWithoutExpirationWithGenericMessage() throws Exception {
        String code = createCode(CORRECT_PASSWORD);
        jdbcTemplate.update("update unity_access_codes set expires_at = null where user_id = ?", user.getId());

        assertUnityLoginRejected(code);

        mockMvc.perform(
                get("/api/v1/users/me/unity-access-code")
                    .header("Authorization", "Bearer " + accessToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exists").value(true))
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void shouldCreateNewCodeWhenStoredCodeIsExpired() throws Exception {
        String expiredCode = createCode(CORRECT_PASSWORD);
        updateStoredExpiration(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        String replacementCode = createCode(CORRECT_PASSWORD);

        assertThat(replacementCode).isNotEqualTo(expiredCode);
        assertThat(unityAccessCodeRepository.count()).isEqualTo(1);
        assertUnityLoginRejected(expiredCode);
        assertUnityLoginAccepted(replacementCode);
    }

    @Test
    void shouldRejectInvalidBlockedAndUnverifiedUnityLoginsWithGenericMessage() throws Exception {
        assertUnityLoginRejected("INVALID");

        User blockedUser = createUser("Blocked User", "blocked@toastedvr.test", "blockedUnity", true, true);
        String blockedCode = createCodeForUser(blockedUser, CORRECT_PASSWORD);
        blockedUser.block();
        userRepository.save(blockedUser);
        assertUnityLoginRejected(blockedCode);

        User unverifiedUser = createUser("Pending User", "pending@toastedvr.test", "pendingUnity", false, true);
        String unverifiedCode = createCodeForUser(unverifiedUser, CORRECT_PASSWORD);
        assertUnityLoginRejected(unverifiedCode);
    }

    private String createCode(String currentPassword) throws Exception {
        return createCodeForUser(user, currentPassword);
    }

    private String createCodeForUser(User targetUser, String currentPassword) throws Exception {
        String targetToken = jwtService.generateToken(new UserPrincipal(targetUser));
        String response = mockMvc.perform(
                post("/api/v1/users/me/unity-access-code")
                    .header("Authorization", "Bearer " + targetToken)
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("currentPassword", currentPassword)))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return responseField(response, "code");
    }

    private UnityAccessCode storedCode() {
        return unityAccessCodeRepository.findByUserId(user.getId()).orElseThrow();
    }

    private void updateStoredExpiration(LocalDateTime expiresAt) {
        jdbcTemplate.update(
            "update unity_access_codes set expires_at = ? where user_id = ?",
            Timestamp.valueOf(expiresAt),
            user.getId()
        );
    }

    private @NonNull MockHttpServletRequestBuilder authenticatedPost(
        @NonNull String path,
        @NonNull String currentPassword
    ) throws Exception {
        return post(path)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(jsonMediaType())
            .content(requireJson(Map.of("currentPassword", currentPassword)));
    }

    private void assertUnityLoginAccepted(String code) throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/unity-login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("code", code)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").value(user.getId()));
    }

    private void assertUnityLoginRejected(String code) throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/unity-login")
                    .contentType(jsonMediaType())
                    .content(requireJson(Map.of("code", code)))
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid Unity access code."));
    }

    private User createUser(String name, String email, String username, boolean verified, boolean enabled) {
        User newUser = new User(name, email, username, passwordEncoder.encode(CORRECT_PASSWORD));
        if (verified) {
            newUser.markEmailAsVerified();
        }
        if (!enabled) {
            newUser.block();
        }
        return userRepository.save(newUser);
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

    @SuppressWarnings("null")
    private static @NonNull SimpleMailMessage anySimpleMailMessage() {
        return any(SimpleMailMessage.class);
    }

    private String responseField(String response, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        return root.get(fieldName).asText();
    }
}
