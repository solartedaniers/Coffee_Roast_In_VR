package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

// ================================================================
// OllamaFeedbackService
// Responsabilidad única: llamar al endpoint HTTP de Ollama con el
// prompt ya armado y devolver el texto de respuesta. No construye el
// prompt (ver RoastFeedbackPromptBuilder) ni conoce el dominio del tueste.
// ================================================================
@Service
public class OllamaFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(OllamaFeedbackService.class);
    private static final String MODEL_NAME = "phi4-mini";
    private static final String GENERATE_ENDPOINT = "/api/generate";
    private static final String RESPONSE_FIELD = "response";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    // Phi-4-mini sobre CPU puede tardar hasta ~50s solo en cargar el modelo
    // si Ollama lo había descargado de memoria (ver KEEP_ALIVE_DURATION).
    private static final int READ_TIMEOUT_MS = 120_000;
    // Le pide a Ollama que mantenga el modelo cargado en memoria tras cada
    // respuesta, para que la siguiente petición no pague de nuevo el costo
    // de carga (~30-50s en frío).
    private static final String KEEP_ALIVE_DURATION = "10m";

    private final RestClient restClient;

    public OllamaFeedbackService(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    // Devuelve null si Ollama no está disponible o falla — es un extra
    // sobre el resultado ya calculado, nunca debe tumbar el flujo principal.
    public String generateFeedback(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        try {
            Map<String, Object> requestBody = Map.of(
                "model", MODEL_NAME,
                "prompt", RoastFeedbackPromptBuilder.build(session, knowledgeLevel),
                "stream", false,
                "keep_alive", KEEP_ALIVE_DURATION,
                // phi4-mini a veces sigue generando texto tras un salto de línea
                // doble (repite instrucciones o inventa una sección nueva).
                // "stop" lo evita casi siempre; el corte en cutAtFirstBlankLine
                // es el respaldo para cuando no lo evita.
                "stop", java.util.List.of("\n\n"),
                "options", Map.of("temperature", 0.4, "num_predict", 240)
            );

            Map<String, Object> response = restClient.post()
                .uri(GENERATE_ENDPOINT)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });

            Object text = response != null ? response.get(RESPONSE_FIELD) : null;
            return text instanceof String value ? cutAtFirstBlankLine(value.strip()) : null;
        } catch (Exception e) {
            log.warn("No se pudo generar retroalimentación con Ollama: {}", e.getMessage());
            return null;
        }
    }

    // Respaldo para cuando el "stop" de la petición no frena a tiempo: el
    // modelo repite instrucciones o inventa contenido nuevo después de un
    // salto de línea doble, así que solo nos quedamos con lo anterior.
    private static String cutAtFirstBlankLine(String text) {
        int blankLineIndex = text.indexOf("\n\n");
        String trimmed = blankLineIndex >= 0 ? text.substring(0, blankLineIndex).strip() : text;
        return dropTrailingPartialSentence(trimmed);
    }

    // num_predict corta la generación por presupuesto de tokens, no por
    // palabra completa, así que a veces la respuesta termina a mitad de una
    // palabra. Si el texto no cierra en una oración, se recorta hasta la
    // última que sí cierre.
    private static String dropTrailingPartialSentence(String text) {
        if (text.isEmpty() || isSentenceEnding(text.charAt(text.length() - 1))) {
            return text;
        }

        int lastSentenceEnd = Math.max(text.lastIndexOf('.'), Math.max(text.lastIndexOf('!'), text.lastIndexOf('?')));
        return lastSentenceEnd >= 0 ? text.substring(0, lastSentenceEnd + 1).strip() : text;
    }

    private static boolean isSentenceEnding(char character) {
        return character == '.' || character == '!' || character == '?';
    }
}
