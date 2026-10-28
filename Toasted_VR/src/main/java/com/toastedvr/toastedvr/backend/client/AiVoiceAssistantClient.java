package com.toastedvr.toastedvr.backend.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.dto.AiVoiceQuestionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Reenvía la pregunta por voz (audio y contexto) al servicio IA (carpeta IA/).
@Component
public class AiVoiceAssistantClient {

    private static final String QUESTIONS_ENDPOINT = "/api/v1/voice/questions";
    private static final String AUDIO_PART = "audio";
    private static final String CONTEXT_PART = "context";
    private static final String AUDIO_FILE_NAME = "question.wav";
    private static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiVoiceAssistantClient(@Qualifier("aiVoiceRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("null")
    public AiVoiceReply ask(AiVoiceQuestionContext context, byte[] wav) {
        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(AUDIO_WAV);
        audioHeaders.setContentDispositionFormData(AUDIO_PART, AUDIO_FILE_NAME);
        HttpHeaders contextHeaders = new HttpHeaders();
        contextHeaders.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add(AUDIO_PART, new HttpEntity<>(wav, audioHeaders));
        parts.add(CONTEXT_PART, new HttpEntity<>(toJson(context), contextHeaders));
        try {
            // Cualquier estado del servicio IA (también 4xx) se devuelve tal cual, sin lanzar.
            return restClient.post()
                .uri(QUESTIONS_ENDPOINT)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .exchange((request, response) ->
                    new AiVoiceReply(response.getStatusCode().value(), response.getBody().readAllBytes()));
        } catch (RestClientException e) {
            throw new AiServiceUnavailableException("El servicio IA no respondió la pregunta por voz", e);
        }
    }

    private String toJson(AiVoiceQuestionContext context) {
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el contexto de la pregunta por voz", e);
        }
    }
}
