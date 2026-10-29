package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiAlertPackageClientTests {

    private static final String PACKAGE_URL = "http://ai.test/api/v1/alerts/package";
    private static final String ETAG = "\"abc123\"";

    private MockRestServiceServer aiService;
    private AiAlertPackageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiAlertPackageClient(builder.build());
    }

    @Test
    void asksForThePackageOfTheLevelAndKeepsItsEtag() {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(ETAG);
        aiService.expect(requestTo(PACKAGE_URL + "?level=ADVANCED"))
            .andExpect(method(Objects.requireNonNull(HttpMethod.GET)))
            .andExpect(headerDoesNotExist(HttpHeaders.IF_NONE_MATCH))
            .andRespond(withSuccess("{\"level\":\"ADVANCED\"}", MediaType.APPLICATION_JSON).headers(headers));

        AiAlertPackageReply reply = client.fetch(KnowledgeLevel.ADVANCED, null);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.etag()).isEqualTo(ETAG);
        assertThat(new String(reply.body(), StandardCharsets.UTF_8)).isEqualTo("{\"level\":\"ADVANCED\"}");
        aiService.verify();
    }

    @Test
    void forwardsIfNoneMatchAndReturnsNotModified() {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(ETAG);
        aiService.expect(requestTo(PACKAGE_URL + "?level=BEGINNER"))
            .andExpect(header(HttpHeaders.IF_NONE_MATCH, ETAG))
            .andRespond(withStatus(HttpStatus.NOT_MODIFIED).headers(headers));

        AiAlertPackageReply reply = client.fetch(KnowledgeLevel.BEGINNER, ETAG);

        assertThat(reply.status()).isEqualTo(304);
        assertThat(reply.body()).isEmpty();
        aiService.verify();
    }

    @Test
    void omitsTheLevelWhenThePlayerHasNone() {
        aiService.expect(requestTo(PACKAGE_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetch(null, " ").status()).isEqualTo(200);
        aiService.verify();
    }

    @Test
    void reportsTheAiServiceAsUnavailableWhenItCannotBeReached() {
        aiService.expect(requestTo(PACKAGE_URL + "?level=BEGINNER"))
            .andRespond(withException(new ConnectException("refused")));

        assertThatThrownBy(() -> client.fetch(KnowledgeLevel.BEGINNER, null))
            .isInstanceOf(AiServiceUnavailableException.class);
    }
}
