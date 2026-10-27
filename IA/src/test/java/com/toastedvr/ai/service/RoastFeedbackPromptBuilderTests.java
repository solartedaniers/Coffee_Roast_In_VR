package com.toastedvr.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.toastedvr.ai.service.RecordedRoastSessions.RecordedSession;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RoastFeedbackPromptBuilderTests {

    private static Locale originalLocale;

    @BeforeAll
    static void useRecordedLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    static Stream<RecordedSession> recordedSessions() {
        return RecordedRoastSessions.ALL.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedSessions")
    void buildsTheSamePromptAsTheBackendDid(RecordedSession recorded) {
        String prompt = RoastFeedbackPromptBuilder.build(recorded.request(), recorded.level(), recorded.context());

        assertThat(prompt).isEqualTo(recorded.expectedPrompt());
    }

    @Test
    void resolvesRoastPhaseFromFinalTemperature() {
        assertThat(RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(160.0)).isEqualTo("Secado");
        assertThat(RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(200.0)).isEqualTo("Caramelización (reacción de Maillard)");
        assertThat(RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(223.9)).isEqualTo("Primer Crack");
        assertThat(RoastFeedbackPromptBuilder.resolveRoastPhaseLabel(224.0)).isEqualTo("Segundo Crack");
    }
}
