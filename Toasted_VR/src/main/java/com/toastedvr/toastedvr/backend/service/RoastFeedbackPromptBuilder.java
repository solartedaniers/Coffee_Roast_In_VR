package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;

// ================================================================
// RoastFeedbackPromptBuilder
// Responsabilidad única: convertir una sesión de tueste ya evaluada
// en el prompt que se le envía al modelo de retroalimentación. El
// prompt está en español a propósito: es la instrucción que le pide
// al modelo que responda en español, para el texto que sí ve el usuario.
// ================================================================
final class RoastFeedbackPromptBuilder {

    // Todo en un solo bloque "prompt" (sin campo "system" separado): en
    // pruebas directas contra Ollama, dividir instrucciones/datos entre
    // "system" y "prompt" hizo que phi3 alucinara secciones tipo tutorial
    // (Q&A, encabezados) en vez de responder directo. La frase final
    // "Retroalimentación:" es la pista para que el modelo continúe
    // directo con la respuesta en vez de reformular las instrucciones.
    private static final String PROMPT_TEMPLATE = """
        Datos de una sesión de tueste de café en un simulador por computadora \
        (no hay objetos físicos: solo estos números).
        Resultado: %s. Puntaje: %d/100.
        Temperatura de carga: %s. Temperatura objetivo: %.1f°C. Temperatura final: %.1f°C.
        Duración: %d segundos. First crack alcanzado: %s. Tiempo de desarrollo \
        tras el first crack: %s segundos.
        Nivel del usuario: %s.

        Escribe 2 o 3 frases en español, en texto plano, explicando qué salió \
        bien o mal según esos datos y una sugerencia concreta para el próximo \
        tueste. No inventes objetos, herramientas ni escenas que no estén en \
        los datos. No repitas estas instrucciones. No uses markdown ni encabezados.
        %s

        Retroalimentación:""";

    private static final String YES = "sí";
    private static final String NO = "no";
    private static final String NOT_AVAILABLE = "N/A";

    // Rango recomendado para cargar el grano — mismo valor que
    // CHARGE_TEMP_IDEAL_MIN_C/MAX_C en RoastConstants.js del frontend
    // (no hay constantes compartidas entre backend y frontend hoy).
    private static final double CHARGE_IDEAL_MIN_C = 180.0;
    private static final double CHARGE_IDEAL_MAX_C = 200.0;

    private RoastFeedbackPromptBuilder() {
    }

    static String build(RoastingSession session, KnowledgeLevel knowledgeLevel) {
        KnowledgeLevel level = knowledgeLevel != null ? knowledgeLevel : KnowledgeLevel.INTERMEDIATE;
        return PROMPT_TEMPLATE.formatted(
            session.getResult(),
            session.getQualityScore(),
            session.getChargeTemperature() != null
                ? chargeTemperatureText(session.getChargeTemperature())
                : NOT_AVAILABLE,
            session.getTargetTemperature(),
            session.getFinalTemperature(),
            session.getTotalDurationSeconds(),
            Boolean.TRUE.equals(session.isFirstCrackReached()) ? YES : NO,
            session.getDevelopmentTimeSeconds() != null ? session.getDevelopmentTimeSeconds().toString() : NOT_AVAILABLE,
            shortLabelFor(level),
            vocabularyReminderFor(level)
        );
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
    // phi3 cambiara el registro del texto entre niveles.
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
