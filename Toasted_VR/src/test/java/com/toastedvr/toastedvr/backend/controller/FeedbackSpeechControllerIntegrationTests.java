package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.toastedvr.toastedvr.backend.client.AiFeedbackClient;
import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.client.AiSpeechClient;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@SuppressWarnings("null")
@AutoConfigureMockMvc
class FeedbackSpeechControllerIntegrationTests {

    private static final String FEEDBACK = "Bajaste bien el gas en el secado.";
    private static final String AUDIO = "{\"audio\":{\"format\":\"wav\",\"sampleRateHz\":22050,\"base64\":\"UklGRg==\"},\"provider\":\"azure\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoastingSessionRepository roastingSessionRepository;

    @Autowired
    private RoastEvaluationRepository roastEvaluationRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AiSpeechClient aiSpeechClient;

    @MockitoBean
    private AiFeedbackClient aiFeedbackClient;

    private User player;
    private RoastingSession session;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = savePlayer("speech.player@toastedvr.test", "speechPlayer");
        session = roastingSessionRepository.save(new RoastingSession(
            player, 190.0, 210.0, 600, 205.0, 206.0, RoastingResult.PERFECT, 78, true, 90, KnowledgeLevel.BEGINNER));
        when(aiFeedbackClient.generateFeedback(any(), any())).thenReturn(FEEDBACK);
    }

    @AfterEach
    void cleanDatabase() {
        roastEvaluationRepository.deleteAll();
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Así llega de verdad el POST de UnityWebRequest sin uploadHandler.
    @Test
    void readsTheScoreAndTheFeedbackWhenUnitySendsAnEmptyFormUrlEncodedBody() throws Exception {
        when(aiSpeechClient.speak(anyString())).thenReturn(new AiVoiceReply(200, AUDIO.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(player))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(""))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(AUDIO));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(aiSpeechClient).speak(text.capture());
        assertThat(text.getValue()).startsWith("Tu nota final es 78 %, ").endsWith(". " + FEEDBACK);
    }

    @Test
    void acceptsThePostWithoutAnyBodyOrContentType() throws Exception {
        when(aiSpeechClient.speak(anyString())).thenReturn(new AiVoiceReply(200, AUDIO.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(player))).andExpect(status().isOk());
    }

    @Test
    void ignoresAnyTextThatUnitySends() throws Exception {
        when(aiSpeechClient.speak(anyString())).thenReturn(new AiVoiceReply(200, AUDIO.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(player))
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Lee esto que no es la retroalimentación\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(aiSpeechClient).speak(text.capture());
        assertThat(text.getValue()).doesNotContain("Lee esto");
    }

    @Test
    void passesTheErrorOfTheAiServiceThrough() throws Exception {
        String error = "{\"code\":\"SPEECH_UNAVAILABLE\",\"message\":\"No pude leer.\"}";
        when(aiSpeechClient.speak(anyString())).thenReturn(new AiVoiceReply(503, error.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(player)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string(error));
    }

    @Test
    void answers503WithItsCodeWhenTheAiServiceIsDown() throws Exception {
        when(aiSpeechClient.speak(anyString())).thenThrow(new AiServiceUnavailableException("down", null));

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(player)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SPEECH_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("No pude leer la retroalimentación en voz alta. Puedes leerla en el panel."));
    }

    @Test
    void answers404ForASessionOfAnotherPlayerWithoutSpendingCharacters() throws Exception {
        User other = savePlayer("speech.other@toastedvr.test", "speechOther");

        mockMvc.perform(post(speechOf(session)).header("Authorization", bearer(other))).andExpect(status().isNotFound());
        verify(aiSpeechClient, never()).speak(anyString());
    }

    @Test
    void answers404ForASessionThatDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/v1/roasting/sessions/999999/feedback/speech").header("Authorization", bearer(player)))
            .andExpect(status().isNotFound());
        verify(aiSpeechClient, never()).speak(anyString());
    }

    private User savePlayer(String email, String username) {
        User user = new User("Player", email, username, passwordEncoder.encode("Password123!"));
        user.markEmailAsVerified();
        user.assignRole(Role.PLAYER);
        user.updateKnowledgeLevel(KnowledgeLevel.BEGINNER);
        return userRepository.save(Objects.requireNonNull(user));
    }

    private static String speechOf(RoastingSession saved) {
        return "/api/v1/roasting/sessions/" + saved.getId() + "/feedback/speech";
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(user));
    }
}
