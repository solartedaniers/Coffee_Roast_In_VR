package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class RagContextRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagContextRetrievalService.class);
    private static final int TOP_K = 4;

    private final VectorStore vectorStore;

    public RagContextRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // Returns an empty list instead of throwing on any failure (vector store
    // unreachable, index still empty before any PDF is ingested, etc.) — same
    // defensive contract as OllamaFeedbackService.generateFeedback: retrieval
    // is an enhancement on top of the existing feedback, never a reason to
    // break it.
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
    // academic material in Spanish. Not empirically tuned yet — rag-docs/ is
    // empty until real PDFs are ingested — revisit topK/query wording once
    // retrieval quality can be checked against the actual documents.
    private static String buildQuery(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        KnowledgeLevel level = knowledgeLevel != null ? knowledgeLevel : KnowledgeLevel.INTERMEDIATE;
        String phaseLabel = RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(session.getFinalTemperature());
        return "Tueste de café. Resultado: %s. Puntaje: %d/100. Fase alcanzada: %s. Nivel del usuario: %s."
            .formatted(session.getResult(), session.getQualityScore(), phaseLabel, level);
    }
}
