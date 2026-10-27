package com.toastedvr.toastedvr.backend.client;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.AiFeedbackRequest;
import com.toastedvr.toastedvr.backend.dto.AiFeedbackResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Pide la retroalimentación de una sesión al servicio IA (carpeta IA/).
@Component
public class AiFeedbackClient {

    private static final Logger log = LoggerFactory.getLogger(AiFeedbackClient.class);
    private static final String FEEDBACK_ENDPOINT = "/api/v1/feedback";

    private final RestClient restClient;

    public AiFeedbackClient(@Qualifier("aiFeedbackRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // Devuelve null si el servicio IA no responde o falla, igual que antes
    // cuando Ollama no estaba disponible: la retroalimentación es un extra y
    // nunca debe tumbar la consulta de la sesión.
    @SuppressWarnings("null")
    public String generateFeedback(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        try {
            AiFeedbackResponse response = restClient.post()
                .uri(FEEDBACK_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(AiFeedbackRequest.from(session, knowledgeLevel))
                .retrieve()
                .body(AiFeedbackResponse.class);
            return response != null ? response.feedback() : null;
        } catch (Exception e) {
            log.warn("No se pudo obtener la retroalimentación del servicio IA: {}", e.getMessage());
            return null;
        }
    }
}
