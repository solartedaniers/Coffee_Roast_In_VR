package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiEvaluationClientTests {

    private static final String EVALUATION_URL = "http://ai.test/api/v1/evaluation";
    private static final String TRACE = "{\"version\":1,\"operationMode\":\"MANUAL\"}";

    private MockRestServiceServer aiService;
    private AiEvaluationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiEvaluationClient(builder.build(), new ObjectMapper());
    }

    @Test
    @SuppressWarnings("null")
    void sendsTheStoredTraceAndReadsTheGrade() {
        aiService.expect(requestTo(EVALUATION_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"trace\":" + TRACE + "}", JsonCompareMode.STRICT))
            .andRespond(withSuccess("""
                {"status":"COMPLETED","rubricVersion":"draft","model":"openai/gpt-oss-120b","score":77.8,
                 "criteria":[{"id":"DRYING_GAS","level":3,"maxLevel":3,"justification":"Bien."}],
                 "notApplicable":["ALERT_REACTION"]}
                """, MediaType.APPLICATION_JSON));

        AiEvaluationReply reply = client.evaluate(TRACE);

        assertThat(reply.completed()).isTrue();
        assertThat(reply.score()).isEqualTo(77.8);
        assertThat(reply.criteria()).containsExactly(new AiEvaluationReply.Criterion("DRYING_GAS", 3, 3, "Bien."));
        assertThat(reply.notApplicable()).containsExactly("ALERT_REACTION");
        aiService.verify();
    }

    @Test
    @SuppressWarnings("null")
    void reportsANotApplicableGrade() {
        aiService.expect(requestTo(EVALUATION_URL)).andRespond(withSuccess(
            "{\"status\":\"NOT_APPLICABLE\",\"rubricVersion\":\"draft\",\"model\":null,\"score\":null,"
                + "\"criteria\":[],\"notApplicable\":[\"DRYING_GAS\"]}", MediaType.APPLICATION_JSON));

        assertThat(client.evaluate(TRACE).completed()).isFalse();
    }

    @Test
    @SuppressWarnings("null")
    void aRejectedTraceIsNotWorthRetrying() {
        aiService.expect(requestTo(EVALUATION_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.evaluate(TRACE)).isInstanceOf(AiEvaluationRejectedException.class);
    }

    @Test
    @SuppressWarnings("null")
    void aGroqFailureOrAnUnreachableServiceIsWorthRetrying() {
        aiService.expect(requestTo(EVALUATION_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> client.evaluate(TRACE)).isInstanceOf(AiServiceUnavailableException.class);

        aiService.reset();
        aiService.expect(requestTo(EVALUATION_URL)).andRespond(withException(new IOException("refused")));
        assertThatThrownBy(() -> client.evaluate(TRACE)).isInstanceOf(AiServiceUnavailableException.class);
    }

    @Test
    void anInvalidStoredTraceIsRejectedWithoutCallingTheService() {
        assertThatThrownBy(() -> client.evaluate("{no es json")).isInstanceOf(AiEvaluationRejectedException.class);
        aiService.verify();
    }
}
