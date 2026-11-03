package com.toastedvr.toastedvr.backend.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Pide al servicio IA (carpeta IA/) que lea en voz alta un texto ya armado por el backend.
@Component
public class AiSpeechClient {

    private static final String SPEECH_ENDPOINT = "/api/v1/voice/speech";
    private static final String TEXT_FIELD = "text";

    private final RestClient restClient;

    public AiSpeechClient(@Qualifier("aiSpeechRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @SuppressWarnings("null")
    public AiVoiceReply speak(String text) {
        try {
            // Cualquier estado del servicio IA (también 4xx y 503) se devuelve tal cual, sin lanzar.
            return restClient.post()
                .uri(SPEECH_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(TEXT_FIELD, text))
                .exchange((request, response) ->
                    new AiVoiceReply(response.getStatusCode().value(), response.getBody().readAllBytes()));
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("El servicio IA no leyó el texto en voz alta", e);
        }
    }
}
