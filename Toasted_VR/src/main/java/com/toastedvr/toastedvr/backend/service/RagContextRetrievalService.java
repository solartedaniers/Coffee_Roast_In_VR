package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagContextRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagContextRetrievalService.class);
    private static final int TOP_K = 4;

    private final VectorStore vectorStore;
    // Development Time Ratio (developmentTimeSeconds / totalDurationSeconds)
    // considerado óptimo para cafés de altura de Nariño, 20% como referencia
    // ideal — configurable en application.yml (rag.query.dtr-optimal-*)
    // porque es un criterio de dominio, no una constante de código.
    private final double dtrOptimalMin;
    private final double dtrOptimalMax;

    public RagContextRetrievalService(
        VectorStore vectorStore,
        @Value("${rag.query.dtr-optimal-min:0.18}") double dtrOptimalMin,
        @Value("${rag.query.dtr-optimal-max:0.22}") double dtrOptimalMax
    ) {
        this.vectorStore = vectorStore;
        this.dtrOptimalMin = dtrOptimalMin;
        this.dtrOptimalMax = dtrOptimalMax;
    }

    // Returns an empty list instead of throwing on any failure (vector store
    // unreachable, index still empty before any PDF is ingested, etc.) — same
    // defensive contract as OllamaFeedbackService.generateFeedback: retrieval
    // is an enhancement on top of the existing feedback, never a reason to
    // break it.
    @SuppressWarnings("null")
    public List<String> findRelevantChunks(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        try {
            SearchRequest request = SearchRequest.builder()
                .query(buildQuery(session, knowledgeLevel))
                .topK(TOP_K)
                .build();
            List<Document> results = vectorStore.similaritySearch(request);
            logRetrievedChunks(results);
            return results.stream().map(Document::getText).toList();
        } catch (Exception e) {
            log.warn("RAG retrieval failed, continuing without extra context: {}", e.getMessage());
            return List.of();
        }
    }

    // Explicit, always-on log (not debug-gated) so retrieval can be verified
    // straight from the application logs: which source PDF and score each
    // retrieved chunk came from, for a given feedback request.
    private static void logRetrievedChunks(List<Document> results) {
        if (results.isEmpty()) {
            log.info("RAG retrieval returned 0 chunks (empty index or no match above threshold)");
            return;
        }
        for (Document chunk : results) {
            log.info(
                "RAG retrieved chunk from file={} score={}: \"{}\"",
                chunk.getMetadata().getOrDefault("file_name", "unknown"),
                chunk.getScore(),
                preview(chunk.getText())
            );
        }
    }

    private static String preview(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    // Kept in Spanish on purpose (same reasoning as RoastFeedbackPromptBuilder):
    // reuses the phase/result vocabulary already used in the final prompt so
    // the query matches the language of the PDFs under rag-docs/, which are
    // academic material in Spanish. Only reads session fields — never writes
    // to the session or touches scoring/phase logic, both owned elsewhere
    // (RoastingSessionService / RoastFeedbackPromptBuilder).
    private String buildQuery(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        KnowledgeLevel level = knowledgeLevel != null ? knowledgeLevel : KnowledgeLevel.INTERMEDIATE;
        String phaseLabel = RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(session.getFinalTemperature());
        StringBuilder query = new StringBuilder(
            "Tueste de café. Resultado: %s. Puntaje: %d/100. Fase alcanzada: %s. Nivel del usuario: %s."
                .formatted(session.getResult(), session.getQualityScore(), phaseLabel, level)
        );

        boolean belowTarget = session.getFinalTemperature() < session.getTargetTemperature();
        boolean aboveTarget = session.getFinalTemperature() > session.getTargetTemperature();
        if (belowTarget) {
            query.append(' ').append(
                "Temperatura final por debajo del objetivo (%.1f°C vs %.1f°C, faltaron %.1f°C).".formatted(
                    session.getFinalTemperature(),
                    session.getTargetTemperature(),
                    session.getTargetTemperature() - session.getFinalTemperature()
                )
            );
        } else if (aboveTarget) {
            query.append(' ').append(
                "Temperatura final por encima del objetivo (%.1f°C vs %.1f°C, %.1f°C de exceso).".formatted(
                    session.getFinalTemperature(),
                    session.getTargetTemperature(),
                    session.getFinalTemperature() - session.getTargetTemperature()
                )
            );
        }

        Double dtr = developmentTimeRatio(session);
        boolean underDeveloped = dtr != null && dtr < dtrOptimalMin;
        boolean overDeveloped = dtr != null && dtr > dtrOptimalMax;
        if (underDeveloped) {
            query.append(' ').append("Tiempo de desarrollo corto tras el first crack, riesgo de subdesarrollo.");
        } else if (overDeveloped) {
            query.append(' ').append("Tiempo de desarrollo prolongado tras el first crack, riesgo de sobre-tueste.");
        }

        // Solo cuando ambas condiciones extremas coinciden a la vez, para no
        // forzar un término de defecto que no aplica realmente a la sesión.
        if (belowTarget && overDeveloped) {
            query.append(' ').append("Posible horneado.");
        } else if (aboveTarget && underDeveloped) {
            query.append(' ').append("Posible quemado.");
        }

        Double chargeTemperature = session.getChargeTemperature();
        if (chargeTemperature != null
            && (chargeTemperature < RoastFeedbackPromptBuilder.CHARGE_IDEAL_MIN_C
                || chargeTemperature > RoastFeedbackPromptBuilder.CHARGE_IDEAL_MAX_C)) {
            query.append(' ').append(
                "Temperatura de carga fuera del rango ideal (%.1f°C).".formatted(chargeTemperature)
            );
        }

        return query.toString();
    }

    private static Double developmentTimeRatio(RoastingSession session) {
        Integer developmentTime = session.getDevelopmentTimeSeconds();
        Integer totalDuration = session.getTotalDurationSeconds();
        if (developmentTime == null || totalDuration == null || totalDuration <= 0) {
            return null;
        }
        return developmentTime / (double) totalDuration;
    }
}
