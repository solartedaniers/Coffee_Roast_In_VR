package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.dto.AiVoiceQuestionContext;
import com.toastedvr.toastedvr.backend.dto.RoastStateRequest;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiVoiceAssistantClientTests {

    private static final String QUESTIONS_URL = "http://ai.test/api/v1/voice/questions";
    private static final UUID ATTEMPT = UUID.fromString("8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10");
    private static final byte[] WAV = "RIFF-fake-wav".getBytes(StandardCharsets.US_ASCII);

    private final AiVoiceQuestionContext context = new AiVoiceQuestionContext(
        7L, KnowledgeLevel.BEGINNER, ATTEMPT,
        new RoastStateRequest(300.0, 190.5, false, 205.0, 190.0, null, 70.0, null)
    );

    private MockRestServiceServer aiService;
    private AiVoiceAssistantClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiVoiceAssistantClient(builder.build(), new ObjectMapper());
    }

    @Test
    @SuppressWarnings("null")
    void sendsTheAudioAndTheServerSideContextAsMultipart() {
        aiService.expect(requestTo(QUESTIONS_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", containsString("multipart/form-data")))
            .andExpect(content().string(allOf(
                containsString("name=\"audio\"; filename=\"question.wav\""),
                containsString("RIFF-fake-wav"),
                containsString("name=\"context\""),
                containsString("\"userId\":7"),
                containsString("\"knowledgeLevel\":\"BEGINNER\""),
                containsString("\"roastAttemptId\":\"8a7c6f0e-2a55-4c1e-9d43-0b6f1f3d2e10\""),
                containsString("\"beanTemperature\":190.5"),
                containsString("\"heatLevelPercent\":70.0")
            )))
            .andRespond(withSuccess("{\"status\":\"ANSWERED\"}", MediaType.APPLICATION_JSON));

        AiVoiceReply reply = client.ask(context, WAV);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"ANSWERED\"}");
        aiService.verify();
    }

    @Test
    @SuppressWarnings("null")
    void passesErrorsFromTheAiServiceThroughWithoutThrowing() {
        aiService.expect(requestTo(QUESTIONS_URL))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"ASSISTANT_BUSY\"}"));

        AiVoiceReply reply = client.ask(context, WAV);

        assertThat(reply.status()).isEqualTo(409);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).contains("ASSISTANT_BUSY");
    }

    @Test
    void reportsTheAiServiceBeingDown() {
        aiService.expect(requestTo(QUESTIONS_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.ask(context, WAV)).isInstanceOf(AiServiceUnavailableException.class);
    }
}
