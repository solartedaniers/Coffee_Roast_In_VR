package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import java.net.ConnectException;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiFeedbackClientTests {

    private static final String FEEDBACK_URL = "http://ai.test/api/v1/feedback";

    private final RoastingSession session = new RoastingSession(
        null, 230.0, 205.0, 600, 228.0, 229.0, RoastingResult.BURNED, 30, true, 60, KnowledgeLevel.BEGINNER
    );

    private MockRestServiceServer aiService;
    private AiFeedbackClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiFeedbackClient(builder.build());
    }

    @Test
    void sendsTheSessionDataAndTheLevelToTheAiService() {
        aiService.expect(requestTo(FEEDBACK_URL))
            .andExpect(method(Objects.requireNonNull(HttpMethod.POST)))
            .andExpect(jsonPath("$.result").value("BURNED"))
            .andExpect(jsonPath("$.qualityScore").value(30))
            .andExpect(jsonPath("$.chargeTemperature").value(230.0))
            .andExpect(jsonPath("$.targetTemperature").value(205.0))
            .andExpect(jsonPath("$.finalTemperature").value(228.0))
            .andExpect(jsonPath("$.totalDurationSeconds").value(600))
            .andExpect(jsonPath("$.firstCrackReached").value(true))
            .andExpect(jsonPath("$.developmentTimeSeconds").value(60))
            .andExpect(jsonPath("$.knowledgeLevel").value("ADVANCED"))
            .andRespond(withSuccess("{\"feedback\":\"Baja la carga.\"}", MediaType.APPLICATION_JSON));

        String feedback = client.generateFeedback(session, KnowledgeLevel.ADVANCED);

        assertThat(feedback).isEqualTo("Baja la carga.");
        aiService.verify();
    }

    @Test
    void returnsNullWhenTheAiServiceHadNoFeedback() {
        aiService.expect(requestTo(FEEDBACK_URL))
            .andRespond(withSuccess("{\"feedback\":null}", MediaType.APPLICATION_JSON));

        assertThat(client.generateFeedback(session, KnowledgeLevel.BEGINNER)).isNull();
    }

    @Test
    void returnsNullWhenTheAiServiceFails() {
        aiService.expect(requestTo(FEEDBACK_URL)).andRespond(withServerError());

        assertThat(client.generateFeedback(session, KnowledgeLevel.BEGINNER)).isNull();
    }

    @Test
    void returnsNullWhenTheAiServiceIsNotRunning() {
        aiService.expect(requestTo(FEEDBACK_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThat(client.generateFeedback(session, KnowledgeLevel.BEGINNER)).isNull();
    }
}
