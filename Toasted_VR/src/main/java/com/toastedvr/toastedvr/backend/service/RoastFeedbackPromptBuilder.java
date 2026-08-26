package com.toastedvr.toastedvr.backend.service;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
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
        Fase del tueste alcanzada al finalizar: %s.
        Duración: %d segundos. First crack alcanzado: %s. Tiempo de desarrollo \
        tras el first crack: %s segundos.
        Nivel del usuario: %s.
        %s
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
            resolveRoastPhaseLabel(session.getFinalTemperature()),
            session.getTotalDurationSeconds(),
            Boolean.TRUE.equals(session.isFirstCrackReached()) ? YES : NO,
            session.getDevelopmentTimeSeconds() != null ? session.getDevelopmentTimeSeconds().toString() : NOT_AVAILABLE,
            shortLabelFor(level),
            secondCrackZoneNote(session),
            vocabularyReminderFor(level)
        );
    }

    // Traduce la temperatura final a la fase oficial del tueste alcanzada
    // (Secado, Caramelización, Primer Crack o Segundo Crack) — mismo umbral
    // que GrainAppearanceModel.getGrainStateName() del frontend, colapsando
    // sus estados intermedios (DARK) dentro de "Primer Crack", que sigue
    // siendo la fase vigente hasta el segundo crack.
    private static String resolveRoastPhaseLabel(double finalTemperature) {
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

    // Cuando el tueste terminó en zona de segundo crack pero el resultado es
    // BURNED (el techo de quemado queda debajo de esa zona a propósito, ver
    // RoastFlavorProfileDescriber.js del frontend), le pide al modelo que
    // explique la causa real en vez de solo decir "se quemó".
    private static String secondCrackZoneNote(RoastingSession session) {
        boolean reachedSecondCrackZone = session.getResult() == RoastingResult.BURNED
            && session.getFinalTemperature() >= SECOND_CRACK_PHASE_START_C;
        if (!reachedSecondCrackZone) {
            return "";
        }
        return "Importante: esta sesión llegó a temperatura de SEGUNDO CRACK. Debes decir, con esta idea "
            + "exacta y sin suavizarla: la temperatura alcanzada habría sido ideal para un tueste OSCURO, "
            + "pero este simulador valida un perfil de tueste MEDIO, por eso el resultado es quemado. "
            + "No uses frases vagas como \"se tostó correctamente hasta cierto punto\" ni evites nombrar "
            + "el segundo crack o la palabra OSCURO.";
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
