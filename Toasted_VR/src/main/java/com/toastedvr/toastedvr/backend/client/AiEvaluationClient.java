package com.toastedvr.toastedvr.backend.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Pide al servicio IA (carpeta IA/) la calificación de las decisiones de una sesión con la rúbrica.
@Component
public class AiEvaluationClient {

    private static final String EVALUATION_ENDPOINT = "/api/v1/evaluation";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiEvaluationClient(@Qualifier("aiEvaluationRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Lanza AiEvaluationRejectedException si el servicio IA rechaza la traza y
     * AiServiceUnavailableException si no responde o Groq falla (vale la pena reintentar).
     */
    @SuppressWarnings("null")
    public AiEvaluationReply evaluate(String traceJson) {
        Map<String, Object> body;
        try {
            body = Map.of("trace", objectMapper.readTree(traceJson));
        } catch (JsonProcessingException exception) {
            throw new AiEvaluationRejectedException("La traza guardada no es JSON válido", exception);
        }
        try {
            return Objects.requireNonNull(restClient.post()
                .uri(EVALUATION_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AiEvaluationReply.class));
        } catch (HttpClientErrorException exception) {
            throw new AiEvaluationRejectedException("El servicio IA rechazó la traza: " + exception.getStatusCode(), exception);
        } catch (RestClientException | NullPointerException exception) {
            throw new AiServiceUnavailableException("El servicio IA no calificó la sesión", exception);
        }
    }
}
