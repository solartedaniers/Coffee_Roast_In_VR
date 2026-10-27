package com.toastedvr.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.toastedvr.ai.config.OllamaProperties;
import com.toastedvr.ai.domain.KnowledgeLevel;
import com.toastedvr.ai.dto.RoastFeedbackRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Usa un servidor HTTP local en lugar de Ollama para revisar exactamente
// qué se le envía al modelo y cómo se recorta la respuesta.
class OllamaFeedbackServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RagContextRetrievalService ragContextRetrievalService = mock(RagContextRetrievalService.class);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> ollamaResponse = new AtomicReference<>();

    private HttpServer ollama;

    @BeforeEach
    void startFakeOllama() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollama.createContext("/api/generate", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ollamaResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        ollama.start();
        when(ragContextRetrievalService.findRelevantChunks(any(), any())).thenReturn(List.of("Contexto."));
    }

    @AfterEach
    void stopFakeOllama() {
        ollama.stop(0);
    }

    @Test
    void sendsThePromptWithTheSameGenerationOptions() throws Exception {
        ollamaResponse.set(ollamaJson("Buen tueste."));
        RoastFeedbackRequest request = RecordedRoastSessions.ALL.get(0).request();

        newService().generateFeedback(request, KnowledgeLevel.BEGINNER);

        JsonNode body = objectMapper.readTree(receivedBody.get());
        assertThat(body.get("model").asText()).isEqualTo("phi4-mini");
        assertThat(body.get("stream").asBoolean()).isFalse();
        assertThat(body.get("keep_alive").asText()).isEqualTo("10m");
        assertThat(body.get("stop").get(0).asText()).isEqualTo("\n\n");
        assertThat(body.get("options").get("temperature").asDouble()).isEqualTo(0.4);
        assertThat(body.get("options").get("num_predict").asInt()).isEqualTo(240);
        assertThat(body.get("prompt").asText())
            .isEqualTo(RoastFeedbackPromptBuilder.build(request, KnowledgeLevel.BEGINNER, List.of("Contexto.")));
    }

    @Test
    void keepsOnlyTheTextBeforeTheFirstBlankLine() throws Exception {
        ollamaResponse.set(ollamaJson("Buen tueste. Sube la carga.\n\nInstrucciones repetidas."));

        String feedback = newService().generateFeedback(RecordedRoastSessions.ALL.get(0).request(), KnowledgeLevel.BEGINNER);

        assertThat(feedback).isEqualTo("Buen tueste. Sube la carga.");
    }

    @Test
    void dropsTheTrailingPartialSentence() throws Exception {
        ollamaResponse.set(ollamaJson("Buen tueste. La próxima vez sube"));

        String feedback = newService().generateFeedback(RecordedRoastSessions.ALL.get(0).request(), KnowledgeLevel.BEGINNER);

        assertThat(feedback).isEqualTo("Buen tueste.");
    }

    @Test
    void returnsNullWhenOllamaIsNotAvailable() {
        ollama.stop(0);

        String feedback = newService().generateFeedback(RecordedRoastSessions.ALL.get(0).request(), KnowledgeLevel.BEGINNER);

        assertThat(feedback).isNull();
    }

    // Solo se cambia la URL: el resto son los valores por defecto, que deben
    // seguir siendo los que tenía el backend.
    private OllamaFeedbackService newService() {
        OllamaProperties properties = new OllamaProperties();
        properties.setBaseUrl("http://127.0.0.1:" + ollama.getAddress().getPort());
        return new OllamaFeedbackService(properties, ragContextRetrievalService);
    }

    private String ollamaJson(String response) throws Exception {
        return objectMapper.writeValueAsString(Map.of("response", response));
    }
}
