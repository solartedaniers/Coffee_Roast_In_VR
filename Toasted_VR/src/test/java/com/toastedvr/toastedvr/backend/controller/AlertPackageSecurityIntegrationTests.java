package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toastedvr.toastedvr.backend.client.AiAlertPackageClient;
import com.toastedvr.toastedvr.backend.client.AiAlertPackageReply;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// El paquete de alertas exige lo mismo que POST /api/v1/roasting/sessions
// (usuario autenticado), solo se abre por GET y las demás rutas no cambian.
@SpringBootTest
@SuppressWarnings("null")
@AutoConfigureMockMvc
class AlertPackageSecurityIntegrationTests {

    private static final String ALERTS = "/api/v1/roasting/assistant/alerts";
    private static final String ETAG = "\"abc123\"";
    private static final String PACKAGE = "{\"level\":\"ADVANCED\",\"rules\":[]}";

    @Autowired
    private MockMvc mockMvc;

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

    @MockitoBean
    private AiAlertPackageClient aiAlertPackageClient;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "alerts@toastedvr.test", "alertsUser", passwordEncoder.encode("Password123!"));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.ADVANCED);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRejectThePackageWithoutToken() throws Exception {
        mockMvc.perform(get(ALERTS)).andExpect(status().isUnauthorized());
        verify(aiAlertPackageClient, never()).fetch(any(), any());
    }

    @Test
    void shouldRejectThePackageWithAnInvalidToken() throws Exception {
        mockMvc.perform(get(ALERTS).header("Authorization", "Bearer not-a-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDeliverThePackageOfTheLevelStoredForThePlayer() throws Exception {
        when(aiAlertPackageClient.fetch(eq(KnowledgeLevel.ADVANCED), any()))
            .thenReturn(new AiAlertPackageReply(200, ETAG, PACKAGE.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(ALERTS).header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(header().string("ETag", ETAG))
            .andExpect(content().json(PACKAGE));
    }

    @Test
    void shouldAnswerNotModifiedWhenUnityAlreadyHasThePackage() throws Exception {
        when(aiAlertPackageClient.fetch(KnowledgeLevel.ADVANCED, ETAG))
            .thenReturn(new AiAlertPackageReply(304, ETAG, new byte[0]));

        int status = mockMvc.perform(get(ALERTS).header("Authorization", bearer()).header("If-None-Match", ETAG))
            .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(304);
    }

    @Test
    void shouldOnlyOpenGetOnThePackage() throws Exception {
        mockMvc.perform(post(ALERTS).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(put(ALERTS).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(delete(ALERTS).header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(get(ALERTS + "/other").header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    @Test
    void shouldKeepTheExistingRulesUnchanged() throws Exception {
        mockMvc.perform(post("/api/v1/roasting/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/roasting/assistant/questions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/roasting/assistant/questions").header("Authorization", bearer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/roasting/sessions").header("Authorization", bearer())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", bearer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/unknown").header("Authorization", bearer())).andExpect(status().isForbidden());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(player));
    }
}
