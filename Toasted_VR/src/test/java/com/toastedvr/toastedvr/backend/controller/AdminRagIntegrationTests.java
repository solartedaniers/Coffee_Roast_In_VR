package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.client.AiIngestionClient;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse.FileResult;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse.Status;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La ingesta del RAG la hace el servicio IA; el backend mantiene la misma
// ruta, la misma restricción a ADMIN y la misma respuesta.
@SpringBootTest
@AutoConfigureMockMvc
class AdminRagIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AiIngestionClient aiIngestionClient;

    @AfterEach
    void cleanDatabase() {
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnTheSummaryFromTheAiServiceToAnAdmin() throws Exception {
        when(aiIngestionClient.ingestAll()).thenReturn(RagIngestionSummaryResponse.from(List.of(
            new FileResult("manual.pdf", Status.INGESTED, 40)
        )));

        mockMvc.perform(post("/api/v1/admin/rag/ingest").header("Authorization", bearer(saveUser("adminUser", Role.ADMIN))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filesIngested").value(1))
            .andExpect(jsonPath("$.totalChunksIndexed").value(40))
            .andExpect(jsonPath("$.files[0].fileName").value("manual.pdf"));
    }

    @Test
    void shouldRejectAPlayerWithoutCallingTheAiService() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rag/ingest").header("Authorization", bearer(saveUser("playerUser", Role.PLAYER))))
            .andExpect(status().isForbidden());

        verify(aiIngestionClient, never()).ingestAll();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }

    private User saveUser(String username, Role role) {
        User user = new User("User", username.toLowerCase() + "@toastedvr.test", username, passwordEncoder.encode(PASSWORD));
        user.markEmailAsVerified();
        user.assignRole(role);
        return userRepository.save(Objects.requireNonNull(user));
    }
}
