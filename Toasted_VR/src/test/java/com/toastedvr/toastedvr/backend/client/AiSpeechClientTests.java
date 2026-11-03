package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("null")
class AiSpeechClientTests {

    private static final String SPEECH_URL = "http://ai.test/api/v1/voice/speech";
    private static final String AUDIO = "{\"audio\":{\"format\":\"wav\",\"sampleRateHz\":22050,\"base64\":\"UklGRg==\"},\"provider\":\"azure\"}";

    private MockRestServiceServer aiService;
    private AiSpeechClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiSpeechClient(builder.build());
    }

    @Test
    void sendsTheTextAsJsonAndPassesTheAudioThrough() {
        aiService.expect(requestTo(SPEECH_URL))
            .andExpect(method(Objects.requireNonNull(HttpMethod.POST)))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"text\":\"Tu nota final es 78 %.\"}", JsonCompareMode.STRICT))
            .andRespond(withSuccess(AUDIO, MediaType.APPLICATION_JSON));

        AiVoiceReply reply = client.speak("Tu nota final es 78 %.");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo(AUDIO);
        aiService.verify();
    }

    @Test
    void passesTheErrorOfTheAiServiceThroughWithoutThrowing() {
        String error = "{\"code\":\"SPEECH_UNAVAILABLE\",\"message\":\"No pude leer.\"}";
        aiService.expect(requestTo(SPEECH_URL))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(error));

        AiVoiceReply reply = client.speak("Hola");

        assertThat(reply.status()).isEqualTo(503);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo(error);
    }

    @Test
    void throwsWhenTheAiServiceIsDown() {
        aiService.expect(requestTo(SPEECH_URL)).andRespond(withException(new ConnectException("refused")));

        assertThatThrownBy(() -> client.speak("Hola")).isInstanceOf(AiServiceUnavailableException.class);
    }
}
