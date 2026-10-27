package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse.Status;
import java.net.ConnectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiIngestionClientTests {

    private static final String INGEST_URL = "http://ai.test/api/v1/rag/ingest";

    private MockRestServiceServer aiService;
    private AiIngestionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        aiService = MockRestServiceServer.bindTo(builder).build();
        client = new AiIngestionClient(builder.build());
    }

    @Test
    @SuppressWarnings("null")
    void returnsTheSummaryReportedByTheAiService() {
        aiService.expect(requestTo(INGEST_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"filesIngested": 1, "filesReingested": 0, "filesSkipped": 1, "filesFailed": 1,
                 "totalChunksIndexed": 52,
                 "files": [
                   {"fileName": "manual.pdf", "status": "INGESTED", "chunkCount": 40, "errorMessage": null},
                   {"fileName": "paper.pdf", "status": "SKIPPED_UNCHANGED", "chunkCount": 12, "errorMessage": null},
                   {"fileName": "roto.pdf", "status": "FAILED", "chunkCount": 0, "errorMessage": "bad text"}
                 ]}
                """, MediaType.APPLICATION_JSON));

        RagIngestionSummaryResponse summary = client.ingestAll();

        assertThat(summary.filesIngested()).isEqualTo(1);
        assertThat(summary.filesSkipped()).isEqualTo(1);
        assertThat(summary.filesFailed()).isEqualTo(1);
        assertThat(summary.totalChunksIndexed()).isEqualTo(52);
        assertThat(summary.files()).hasSize(3);
        assertThat(summary.files().get(2).status()).isEqualTo(Status.FAILED);
        assertThat(summary.files().get(2).errorMessage()).isEqualTo("bad text");
        aiService.verify();
    }

    @Test
    void returnsAnEmptySummaryWhenTheAiServiceFails() {
        aiService.expect(requestTo(INGEST_URL)).andRespond(withServerError());

        assertEmpty(client.ingestAll());
    }

    @Test
    void returnsAnEmptySummaryWhenTheAiServiceIsNotRunning() {
        aiService.expect(requestTo(INGEST_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertEmpty(client.ingestAll());
    }

    private static void assertEmpty(RagIngestionSummaryResponse summary) {
        assertThat(summary.filesIngested()).isZero();
        assertThat(summary.filesReingested()).isZero();
        assertThat(summary.filesSkipped()).isZero();
        assertThat(summary.filesFailed()).isZero();
        assertThat(summary.totalChunksIndexed()).isZero();
        assertThat(summary.files()).isEmpty();
    }
}
