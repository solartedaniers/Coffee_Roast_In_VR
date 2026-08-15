package com.toastedvr.toastedvr.backend.service;

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
        Temperatura objetivo: %.1f°C. Temperatura final: %.1f°C.
        Duración: %d segundos. First crack alcanzado: %s. Tiempo de desarrollo \
        tras el first crack: %s segundos.

        Escribe 2 o 3 frases en español, en texto plano, explicando qué salió \
        bien o mal según esos datos y una sugerencia concreta para el próximo \
        tueste. No inventes objetos, herramientas ni escenas que no estén en \
        los datos. No repitas estas instrucciones. No uses markdown ni encabezados.

        Retroalimentación:""";

    private static final String YES = "sí";
    private static final String NO = "no";
    private static final String NOT_AVAILABLE = "N/A";

    private RoastFeedbackPromptBuilder() {
    }

    static String build(RoastingSession session) {
        return PROMPT_TEMPLATE.formatted(
            session.getResult(),
            session.getQualityScore(),
            session.getTargetTemperature(),
            session.getFinalTemperature(),
            session.getTotalDurationSeconds(),
            Boolean.TRUE.equals(session.isFirstCrackReached()) ? YES : NO,
            session.getDevelopmentTimeSeconds() != null ? session.getDevelopmentTimeSeconds().toString() : NOT_AVAILABLE
        );
    }
}
