package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.client.AiVoiceAssistantClient;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.AiVoiceQuestionContext;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@SuppressWarnings("null")
@AutoConfigureMockMvc
class VoiceAssistantControllerIntegrationTests {

    private static final String QUESTIONS = "/api/v1/roasting/assistant/questions";
    private static final byte[] WAV = "RIFF-fake-wav".getBytes(StandardCharsets.US_ASCII);
    // Unity intenta falsificar usuario y nivel: el servidor los debe ignorar.
    private static final String CONTEXT = """
        {"roastAttemptId":"8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10","userId":999,"knowledgeLevel":"ADVANCED",
         "roastState":{"elapsedSeconds":300,"beanTemperature":190.5,"firstCrackReached":false,"heatLevelPercent":70}}
        """;

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
    private AiVoiceAssistantClient aiVoiceAssistantClient;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "voice@toastedvr.test", "voiceUser", passwordEncoder.encode("Password123!"));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.BEGINNER);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldForwardTheAudioWithTheServerSideUserAndLevelAndReturnTheAiReply() throws Exception {
        String reply = "{\"status\":\"ANSWERED\",\"answer\":\"Falta poco.\"}";
        when(aiVoiceAssistantClient.ask(any(), any())).thenReturn(new AiVoiceReply(200, reply.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(question(WAV, CONTEXT))
            .andExpect(status().isOk())
            .andExpect(content().json(reply));

        ArgumentCaptor<AiVoiceQuestionContext> sent = ArgumentCaptor.forClass(AiVoiceQuestionContext.class);
        ArgumentCaptor<byte[]> audio = ArgumentCaptor.forClass(byte[].class);
        verify(aiVoiceAssistantClient).ask(sent.capture(), audio.capture());
        assertThat(sent.getValue().userId()).isEqualTo(player.getId());
        assertThat(sent.getValue().knowledgeLevel()).isEqualTo(KnowledgeLevel.BEGINNER);
        assertThat(sent.getValue().roastState().heatLevelPercent()).isEqualTo(70.0);
        assertThat(audio.getValue()).isEqualTo(WAV);
    }

    @Test
    void shouldPassTheAiServiceErrorsThroughToUnity() throws Exception {
        when(aiVoiceAssistantClient.ask(any(), any()))
            .thenReturn(new AiVoiceReply(429, "{\"code\":\"RATE_LIMITED\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(question(WAV, CONTEXT))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void shouldAnswer503WhenTheAiServiceIsDown() throws Exception {
        when(aiVoiceAssistantClient.ask(any(), any()))
            .thenThrow(new AiServiceUnavailableException("down", new RuntimeException()));

        mockMvc.perform(question(WAV, CONTEXT))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ASSISTANT_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void shouldRejectAnInvalidContextWithoutCallingTheAiService() throws Exception {
        for (String context : new String[] {"{not json", "{\"roastAttemptId\":\"8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10\"}"}) {
            mockMvc.perform(question(WAV, context))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTEXT_INVALID"));
        }
        verify(aiVoiceAssistantClient, never()).ask(any(), any());
    }

    @Test
    void shouldRejectAMissingOrEmptyAudio() throws Exception {
        mockMvc.perform(question(new byte[0], CONTEXT))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUDIO_INVALID"));
        mockMvc.perform(multipart(QUESTIONS).param("context", CONTEXT).header("Authorization", bearer()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("AUDIO_INVALID"));
        verify(aiVoiceAssistantClient, never()).ask(any(), any());
    }

    private MockMultipartHttpServletRequestBuilder question(byte[] wav, String context) {
        return (MockMultipartHttpServletRequestBuilder) multipart(QUESTIONS)
            .file(new MockMultipartFile("audio", "question.wav", "audio/wav", wav))
            .param("context", context)
            .header("Authorization", bearer());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(player));
    }
}
