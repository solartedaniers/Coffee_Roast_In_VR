package com.toastedvr.ai.service;

import com.toastedvr.ai.domain.KnowledgeLevel;
import com.toastedvr.ai.domain.RoastingResult;
import com.toastedvr.ai.dto.RoastFeedbackRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

// Sesiones cuyas salidas (prompt y query de RAG) se grabaron en
// src/test/resources/snapshots con el código del backend antes de mover la
// IA a este servicio. Los tests comparan contra esas salidas para asegurar
// que el comportamiento no cambió. Se grabaron con Locale.US.
final class RecordedRoastSessions {

    record RecordedSession(String name, RoastFeedbackRequest request, KnowledgeLevel level, List<String> context) {

        String expectedPrompt() {
            return readSnapshot(name + "-prompt.txt");
        }

        String expectedQuery() {
            return readSnapshot(name + "-query.txt");
        }
    }

    static final List<RecordedSession> ALL = List.of(
        new RecordedSession(
            "perfect",
            request(190.0, 200.0, 780, 200.0, RoastingResult.PERFECT, 92, true, 156),
            KnowledgeLevel.BEGINNER,
            List.of("Fragmento uno.", "Fragmento dos.")
        ),
        new RecordedSession(
            "burned",
            request(230.0, 205.0, 600, 228.0, RoastingResult.BURNED, 30, true, 60),
            KnowledgeLevel.ADVANCED,
            List.of()
        ),
        new RecordedSession(
            "raw",
            request(null, 210.0, 500, 180.0, RoastingResult.RAW, 20, false, null),
            null,
            null
        ),
        new RecordedSession(
            "baked",
            request(150.0, 215.0, 900, 150.0, RoastingResult.BAKED, 15, true, 300),
            KnowledgeLevel.INTERMEDIATE,
            List.of("Contexto.")
        )
    );

    private RecordedRoastSessions() {
    }

    private static RoastFeedbackRequest request(
        Double chargeTemperature,
        Double targetTemperature,
        Integer totalDurationSeconds,
        Double finalTemperature,
        RoastingResult result,
        Integer qualityScore,
        Boolean firstCrackReached,
        Integer developmentTimeSeconds
    ) {
        return new RoastFeedbackRequest(
            result,
            qualityScore,
            chargeTemperature,
            targetTemperature,
            finalTemperature,
            totalDurationSeconds,
            firstCrackReached,
            developmentTimeSeconds,
            null
        );
    }

    private static String readSnapshot(String fileName) {
        try (InputStream input = RecordedRoastSessions.class.getResourceAsStream("/snapshots/" + fileName)) {
            String content = new String(Objects.requireNonNull(input, fileName).readAllBytes(), StandardCharsets.UTF_8);
            // En Windows git puede dejar los archivos con CRLF.
            return content.replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
