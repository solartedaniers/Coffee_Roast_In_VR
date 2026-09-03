package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.UnityAccessCode;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.UnityAccessCodeRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
            .andExpect(jsonPath("$.createdAt").exists())
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
        String revealedCode = responseField(
            mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/reveal", CORRECT_PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "code"
        );

        assertThat(revealedCode).isEqualTo(originalCode);

        mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/email", CORRECT_PASSWORD))
            .andExpect(status().isNoContent());
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
        assertThat(unityAccessCodeRepository.findByUserId(user.getId()).orElseThrow().getLastEmailedAt()).isNotNull();

        String regeneratedCode = responseField(
            mockMvc.perform(authenticatedPost("/api/v1/users/me/unity-access-code/regenerate", CORRECT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regeneratedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "code"
        );

        assertThat(regeneratedCode).isNotEqualTo(originalCode);
        assertUnityLoginRejected(originalCode);
        assertUnityLoginAccepted(regeneratedCode.toLowerCase().replace("-", " "));
    }

    @Test
    void shouldReturnLoginResponseForValidUnityCode() throws Exception {
        String code = createCode(CORRECT_PASSWORD);

        assertUnityLoginAccepted(code);
    }

    @Test
    void shouldRejectInvalidBlockedAndUnverifiedUnityLoginsWithGenericMessage() throws Exception {
        assertUnityLoginRejected("INVALID");

        User blockedUser = createUser("Blocked User", "blocked@toastedvr.test", "blockedUnity", true, false);
        String blockedCode = createCodeForUser(blockedUser, CORRECT_PASSWORD);
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
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(Map.of("currentPassword", currentPassword)))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return responseField(response, "code");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticatedPost(
        String path,
        String currentPassword
    ) throws Exception {
        return post(path)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJson(Map.of("currentPassword", currentPassword)));
    }

    private void assertUnityLoginAccepted(String code) throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/unity-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(Map.of("code", code)))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").value(user.getId()));
    }

    private void assertUnityLoginRejected(String code) throws Exception {
        mockMvc.perform(
                post("/api/v1/auth/unity-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(Map.of("code", code)))
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

    private String responseField(String response, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        return root.get(fieldName).asText();
    }
}
