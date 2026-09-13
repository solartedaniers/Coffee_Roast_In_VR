package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import java.util.List;

// ================================================================
// RoastFeedbackPromptBuilder
// Responsabilidad única: convertir una sesión de tueste ya evaluada
// en el prompt que se le envía al modelo de retroalimentación. El
// prompt está en español a propósito: es la instrucción que le pide
// al modelo que responda en español, para el texto que sí ve el usuario.
// ================================================================
final class RoastFeedbackPromptBuilder {

    // Todo en un solo bloque "prompt" (sin campo "system" separado): hallazgo
    // histórico con phi3 — dividir instrucciones/datos entre "system" y
    // "prompt" lo hacía alucinar secciones tipo tutorial (Q&A, encabezados)
    // en vez de responder directo. Reverificado directamente contra Ollama
    // con phi4-mini (2026-08-26): con esa misma separación no reprodujo el
    // problema, respondió en prosa plana sin encabezados ni Q&A. La
    // estructura de un solo bloque se mantiene de todas formas porque no
    // tiene costo y sigue siendo la más simple. La frase final
    // "Retroalimentación:" es la pista para que el modelo continúe
    // directo con la respuesta en vez de reformular las instrucciones.
    private static final String PROMPT_TEMPLATE = """
        Datos de una sesión de tueste de café en un simulador por computadora \
        (no hay objetos físicos: solo estos números).
        Resultado: %s. Puntaje: %d/100.
        Temperatura de carga: %s.
        Temperatura objetivo: %.1f°C. Temperatura final: %.1f°C. %s
        Fase del tueste alcanzada al finalizar: %s.
        Duración total: %d segundos. First crack alcanzado: %s.
        %s
        Nivel del usuario: %s.
        Escribe 2 o 3 frases en español, en texto plano, explicando qué salió \
        bien o mal según esos datos y una sugerencia concreta para el próximo \
        tueste. No inventes objetos, herramientas ni escenas que no estén en \
        los datos. No repitas estas instrucciones. No uses markdown ni encabezados.
        %s
        %s
        Recuerda el resultado real de esta sesión: %s (puntaje %d/100). Tu \
        sugerencia debe ser consistente con ese resultado, no contradecirlo.

        Retroalimentación:""";

    private static final String YES = "sí";
    private static final String NO = "no";
    private static final String NOT_AVAILABLE = "N/A";

    // Rango recomendado para cargar el grano — mismo valor que
    // CHARGE_TEMP_IDEAL_MIN_C/MAX_C en RoastConstants.js del frontend
    // (no hay constantes compartidas entre backend y frontend hoy).
    // Visibilidad de paquete (no private): RagContextRetrievalService también
    // las usa para la query de búsqueda en pgvector, mismo criterio que
    // resolveRoastPhaseLabel más abajo.
    static final double CHARGE_IDEAL_MIN_C = 180.0;
    static final double CHARGE_IDEAL_MAX_C = 200.0;

    // Umbrales de fase del tueste — mismos valores que MAILLARD_TEMP_START_C,
    // MAILLARD_TEMP_END_C y SECOND_CRACK_TEMP_MIN_C en RoastConstants.js del
    // frontend (no hay constantes compartidas entre backend y frontend hoy).
    private static final double DRYING_PHASE_END_C = 160.0;
    private static final double MAILLARD_PHASE_END_C = 200.0;
    private static final double SECOND_CRACK_PHASE_START_C = 224.0;

    private static final String DRYING_PHASE_LABEL = "Secado";
    private static final String MAILLARD_PHASE_LABEL = "Caramelización (reacción de Maillard)";
    private static final String FIRST_CRACK_PHASE_LABEL = "Primer Crack";
    private static final String SECOND_CRACK_PHASE_LABEL = "Segundo Crack";

    // Mismo rango que rag.query.dtr-optimal-min/max (application.yml), usado
    // por RagContextRetrievalService para la query de búsqueda — duplicado
    // aquí como texto explícito para el modelo, mismo criterio de "no hay
    // constantes compartidas hoy" que ya aplica a CHARGE_IDEAL_MIN_C/MAX_C.
    private static final double DTR_IDEAL_MIN_RATIO = 0.18;
    private static final double DTR_IDEAL_MAX_RATIO = 0.22;

    private static final String BELOW_TARGET_LABEL = "POR DEBAJO";
    private static final String ABOVE_TARGET_LABEL = "POR ENCIMA";

    private static final String DEVELOPMENT_TIME_SHORT_LABEL = "CORTO";
    private static final String DEVELOPMENT_TIME_LONG_LABEL = "PROLONGADO";
    private static final String DEVELOPMENT_TIME_OPTIMAL_LABEL = "dentro del rango óptimo";

    private RoastFeedbackPromptBuilder() {
    }

    static String build(RoastingSession session, KnowledgeLevel knowledgeLevel, List<String> retrievedContext) {
        KnowledgeLevel level = knowledgeLevel != null ? knowledgeLevel : KnowledgeLevel.INTERMEDIATE;
        return PROMPT_TEMPLATE.formatted(
            session.getResult(),
            session.getQualityScore(),
            session.getChargeTemperature() != null
                ? chargeTemperatureText(session.getChargeTemperature())
                : NOT_AVAILABLE,
            session.getTargetTemperature(),
            session.getFinalTemperature(),
            targetVsFinalDeltaText(session),
            resolveRoastPhaseLabel(session.getFinalTemperature()),
            session.getTotalDurationSeconds(),
            Boolean.TRUE.equals(session.isFirstCrackReached()) ? YES : NO,
            developmentTimeText(session),
            shortLabelFor(level),
            vocabularyReminderFor(level),
            retrievedContextBlock(retrievedContext),
            session.getResult(),
            session.getQualityScore()
        );
    }

    // Deja ya calculada la dirección y magnitud de la diferencia entre
    // objetivo y final, en vez de dejar que el modelo la infiera restando
    // dos números mencionados en oraciones separadas — hallazgo: el modelo
    // llegó a inventar un objetivo que no estaba en los datos reales.
    private static String targetVsFinalDeltaText(RoastingSession session) {
        double delta = session.getFinalTemperature() - session.getTargetTemperature();
        if (delta == 0) {
            return "La temperatura final coincidió exactamente con el objetivo.";
        }
        String direction = delta < 0 ? BELOW_TARGET_LABEL : ABOVE_TARGET_LABEL;
        return "Quedó %.1f°C %s del objetivo.".formatted(Math.abs(delta), direction);
    }

    // Clasifica explícitamente el tiempo de desarrollo (corto/óptimo/
    // prolongado) en vez de dar solo los segundos crudos — hallazgo: el
    // modelo confundió un DTR alto (prolongado) con uno corto. Mismo rango
    // y mismo vocabulario ("corto"/"prolongado") que ya usa la query de
    // RagContextRetrievalService, para no introducir un tercer criterio.
    private static String developmentTimeText(RoastingSession session) {
        Integer developmentTime = session.getDevelopmentTimeSeconds();
        Integer totalDuration = session.getTotalDurationSeconds();
        if (developmentTime == null) {
            return "Tiempo de desarrollo tras el first crack: %s.".formatted(NOT_AVAILABLE);
        }

        double ratio = developmentTime / (double) totalDuration;
        String classification;
        if (ratio < DTR_IDEAL_MIN_RATIO) {
            classification = "esto es %s, no prolongado, respecto al rango óptimo de %.0f%%-%.0f%%"
                .formatted(DEVELOPMENT_TIME_SHORT_LABEL, DTR_IDEAL_MIN_RATIO * 100, DTR_IDEAL_MAX_RATIO * 100);
        } else if (ratio > DTR_IDEAL_MAX_RATIO) {
            classification = "esto es %s, no corto, respecto al rango óptimo de %.0f%%-%.0f%%"
                .formatted(DEVELOPMENT_TIME_LONG_LABEL, DTR_IDEAL_MIN_RATIO * 100, DTR_IDEAL_MAX_RATIO * 100);
        } else {
            classification = DEVELOPMENT_TIME_OPTIMAL_LABEL;
        }
        return "Tiempo de desarrollo tras el first crack: %d segundos de %d segundos totales (%.0f%% del tueste) — %s."
            .formatted(developmentTime, totalDuration, ratio * 100, classification);
    }

    // Bloque opcional con los fragmentos recuperados de rag-docs/ (RAG) más
    // relevantes para esta sesión. Vacío cuando no hay resultados —el prompt
    // queda igual a como era antes de agregar RAG— para que el feedback siga
    // funcionando aunque el índice de pgvector todavía no tenga PDFs cargados.
    private static String retrievedContextBlock(List<String> retrievedContext) {
        if (retrievedContext == null || retrievedContext.isEmpty()) {
            return "";
        }
        StringBuilder block = new StringBuilder(
            "Contexto de referencia de material académico (úsalo solo si es relevante, no lo cites textualmente):"
        );
        for (String chunk : retrievedContext) {
            block.append("\n- ").append(chunk);
        }
        return block.toString();
    }

    // Traduce la temperatura final a la fase oficial del tueste alcanzada
    // (Secado, Caramelización, Primer Crack o Segundo Crack) — mismo umbral
    // que GrainAppearanceModel.getGrainStateName() del frontend, colapsando
    // sus estados intermedios (DARK) dentro de "Primer Crack", que sigue
    // siendo la fase vigente hasta el segundo crack.
    // Visibilidad de paquete (no private): RagContextRetrievalService también
    // la usa para construir la query de búsqueda en pgvector, así ambos
    // puntos comparten la misma fuente de verdad para los umbrales de fase.
    // Nota: cuando la fase es "Segundo Crack" con resultado BURNED, la
    // explicación de que esa temperatura sería válida para un tueste oscuro
    // (pero se penaliza porque el sistema en esta fase del proyecto solo
    // evalúa hasta primer crack) ya la muestra el frontend como texto fijo
    // (RoastFlavorProfileDescriber.js → clave BURNED_SECOND_CRACK_ZONE en
    // es.json), no generado por el LLM. Este prompt solo reporta la fase;
    // no le pide al modelo que la explique — un intento anterior de hacerlo
    // desde aquí no logró que el modelo la sostuviera de forma consistente.
    static String resolveRoastPhaseLabel(double finalTemperature) {
        if (finalTemperature <= DRYING_PHASE_END_C) {
            return DRYING_PHASE_LABEL;
        }
        if (finalTemperature <= MAILLARD_PHASE_END_C) {
            return MAILLARD_PHASE_LABEL;
        }
        if (finalTemperature < SECOND_CRACK_PHASE_START_C) {
            return FIRST_CRACK_PHASE_LABEL;
        }
        return SECOND_CRACK_PHASE_LABEL;
    }

    // Le señala al modelo cuando la carga quedó fuera del rango
    // recomendado, para que lo mencione en la retroalimentación — mismo
    // criterio que ya penaliza el puntaje en ChargeTemperaturePenaltyCalculator.js.
    private static String chargeTemperatureText(double chargeTemperature) {
        boolean outOfRange = chargeTemperature < CHARGE_IDEAL_MIN_C || chargeTemperature > CHARGE_IDEAL_MAX_C;
        String note = outOfRange
            ? " (fuera del rango recomendado de %.0f-%.0f°C)".formatted(CHARGE_IDEAL_MIN_C, CHARGE_IDEAL_MAX_C)
            : "";
        return "%.1f°C%s".formatted(chargeTemperature, note);
    }

    // Traduce el nivel de conocimiento del usuario a la instrucción de
    // vocabulario/detalle que debe seguir el modelo — mismo catálogo de
    // 3 niveles que ya gobierna el puntaje en KnowledgeLevelRules.js del
    // frontend, sin duplicar su definición: aquí solo se mapea el mismo
    // enum de dominio a las frases que necesita este prompt.
    private static String shortLabelFor(KnowledgeLevel level) {
        return switch (level) {
            case BEGINNER -> "principiante, usa vocabulario simple y explica cualquier término técnico que uses";
            case INTERMEDIATE -> "intermedio, usa vocabulario técnico moderado sin explicaciones básicas";
            case ADVANCED -> "avanzado, usa vocabulario técnico de tostador experto y sé directo";
        };
    }

    // Recordatorio con ejemplos concretos de palabras, repetido justo antes
    // de "Retroalimentación:" (donde el modelo empieza a generar) porque una
    // sola mención de nivel entre los datos numéricos no bastaba para que
    // phi3 cambiara el registro del texto entre niveles. Verificado contra
    // Ollama con phi4-mini el 2026-08-26 con dos pruebas: (1) el mismo
    // prompt pero SIN este recordatorio repetido → principiante y avanzado
    // dieron texto casi idéntico (mismo problema heredado de phi3);
    // (2) el prompt REAL completo tal como lo arma build() hoy, CON este
    // recordatorio → el registro sí se diferenció bien (avanzado usó "DTR",
    // "reacción de Maillard" y "ratio de desarrollo"; principiante no usó
    // jerga). Conclusión: el sistema en producción funciona correctamente;
    // el recordatorio sigue siendo necesario para que funcione.
    private static String vocabularyReminderFor(KnowledgeLevel level) {
        return switch (level) {
            case BEGINNER -> "Recuerda: nivel principiante. Usa palabras simples como \"tueste\", "
                + "\"tiempo\" y \"temperatura\"; evita o explica términos como \"ratio de desarrollo\", "
                + "\"Maillard\" o \"DTR\".";
            case INTERMEDIATE -> "Recuerda: nivel intermedio. Puedes usar términos como \"first crack\" "
                + "o \"tiempo de desarrollo\" sin explicarlos, pero evita jerga muy especializada como "
                + "\"DTR\" o \"reacción de Maillard\".";
            case ADVANCED -> "Recuerda: nivel avanzado. Usa términos técnicos como \"DTR\", \"ratio de "
                + "desarrollo\" o \"reacción de Maillard\" libremente, sin explicarlos, y sé directo.";
        };
    }
}
