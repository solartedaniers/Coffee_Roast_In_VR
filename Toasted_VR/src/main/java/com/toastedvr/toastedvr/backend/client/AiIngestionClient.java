package com.toastedvr.toastedvr.backend.client;

import com.toastedvr.toastedvr.backend.dto.RagIngestionSummaryResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Pide al servicio IA (carpeta IA/) que ingeste los PDFs de rag-docs/.
@Component
public class AiIngestionClient {

    private static final Logger log = LoggerFactory.getLogger(AiIngestionClient.class);
    private static final String INGEST_ENDPOINT = "/api/v1/rag/ingest";

    private final RestClient restClient;

    public AiIngestionClient(@Qualifier("aiIngestionRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // Si el servicio IA no responde, el backend no puede saber qué PDFs hay,
    // así que devuelve un resumen vacío (todo en 0) en vez de un error.
    @SuppressWarnings("null")
    public RagIngestionSummaryResponse ingestAll() {
        try {
            RagIngestionSummaryResponse response = restClient.post()
                .uri(INGEST_ENDPOINT)
                .retrieve()
                .body(RagIngestionSummaryResponse.class);
            return response != null ? response : emptySummary();
        } catch (Exception e) {
            log.warn("No se pudo ejecutar la ingesta del RAG en el servicio IA: {}", e.getMessage());
            return emptySummary();
        }
    }

    private static RagIngestionSummaryResponse emptySummary() {
        return RagIngestionSummaryResponse.from(List.of());
    }
}
