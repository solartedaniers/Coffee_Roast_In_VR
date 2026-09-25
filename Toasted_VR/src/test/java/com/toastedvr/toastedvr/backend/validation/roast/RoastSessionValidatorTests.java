package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.config.RoastValidationProperties;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.exception.ErrorCode;
import com.toastedvr.toastedvr.backend.exception.RoastSessionRejectedException;
import com.toastedvr.toastedvr.backend.service.AuditService;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// RF015: cada regla rechaza su caso y acepta justo el límite. Las reglas de
// coherencia (C1-C5) van en una sola dirección, así que también se revisan
// los casos reales que las reglas inversas rechazarían por error.
class RoastSessionValidatorTests {

    private static final long USER_ID = 7L;

    private final AuditService auditService = mock(AuditService.class);
    private final RoastSessionValidator validator = new RoastSessionValidator(allRules(), auditService);

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedSessions")
    void shouldRejectAndAuditTheBrokenRule(String description, Consumer<Session> change, String expectedRule) {
        Session session = Session.valid();
        change.accept(session);

        RoastSessionRejectedException exception = catchThrowableOfType(
            RoastSessionRejectedException.class,
            () -> validator.validate(USER_ID, session.toRequest())
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getCode()).isEqualTo(ErrorCode.ROAST_SESSION_REJECTED);
        assertThat(exception.getDetails()).containsEntry("rule", expectedRule);
        assertThat(exception.getMessage()).startsWith("No se guardó la sesión:");
        verify(auditService).logRoastSessionRejected(eq(USER_ID), eq(expectedRule), eq(exception.getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedSessions")
    void shouldAcceptValidSessionsWithoutAuditing(String description, Consumer<Session> change) {
        Session session = Session.valid();
        change.accept(session);

        assertThatCode(() -> validator.validate(USER_ID, session.toRequest())).doesNotThrowAnyException();
        verify(auditService, never()).logRoastSessionRejected(anyLong(), anyString(), anyString());
    }

    @Test
    void shouldReportTheReceivedValueAndTheLimitsInTheMessage() {
        Session session = Session.valid();
        session.chargeTemperature = 900.0;

        RoastSessionRejectedException exception = catchThrowableOfType(
            RoastSessionRejectedException.class,
            () -> validator.validate(USER_ID, session.toRequest())
        );

        assertThat(exception.getMessage())
            .isEqualTo("No se guardó la sesión: la temperatura de carga (900 °C) debe estar entre 0 y 750 °C.");
    }

    @Test
    void shouldReadTheLimitsFromConfiguration() {
        RoastValidationProperties properties = new RoastValidationProperties();
        properties.setChargeTemperatureMax(300);
        RoastSessionValidator strictValidator = new RoastSessionValidator(
            List.of(new ChargeTemperatureRule(properties, messages())),
            auditService
        );
        Session session = Session.valid();
        session.chargeTemperature = 301.0;

        assertThatCode(() -> strictValidator.validate(USER_ID, session.toRequest()))
            .isInstanceOf(RoastSessionRejectedException.class);
    }

    static Stream<Arguments> rejectedSessions() {
        return Stream.of(
            rejected("carga bajo cero", s -> s.chargeTemperature = -0.5, ChargeTemperatureRule.RULE),
            rejected("carga sobre el máximo del aire", s -> s.chargeTemperature = 750.5, ChargeTemperatureRule.RULE),
            rejected("objetivo bajo el mínimo", s -> s.targetTemperature = 149.9, TargetTemperatureRule.RULE),
            rejected("objetivo sobre el máximo", s -> s.targetTemperature = 230.1, TargetTemperatureRule.RULE),
            rejected("final sobre la temperatura segura", s -> {
                s.finalTemperature = 250.5;
                s.peakTemperature = 251.0;
                s.result = RoastingResult.BURNED;
                s.qualityScore = 5;
            }, FinalTemperatureRule.RULE),
            rejected("pico menor que la final", s -> s.peakTemperature = 204.9, PeakTemperatureRule.RULE),
            rejected("desarrollo mayor que el total", s -> s.developmentTimeSeconds = 781, DevelopmentTimeRule.RULE),
            rejected("C1: PERFECT sin primer crack", s -> {
                s.firstCrackReached = false;
                s.developmentTimeSeconds = 0;
            }, FirstCrackResultRule.RULE),
            rejected("C1: BAKED sin primer crack", s -> {
                s.firstCrackReached = false;
                s.result = RoastingResult.BAKED;
                s.qualityScore = 40;
            }, FirstCrackResultRule.RULE),
            rejected("C2: PERFECT bajo el techo de crudo", s -> s.finalTemperature = 185.9, RawTemperatureResultRule.RULE),
            rejected("C2: BURNED bajo el techo de crudo", s -> {
                s.finalTemperature = 180.0;
                s.result = RoastingResult.BURNED;
                s.qualityScore = 5;
            }, RawTemperatureResultRule.RULE),
            rejected("C3: PERFECT sobre el techo de quemado", s -> {
                s.finalTemperature = 217.1;
                s.peakTemperature = 218.0;
            }, BurnedTemperatureResultRule.RULE),
            rejected("C3: BAKED sobre el techo de quemado", s -> {
                s.finalTemperature = 220.0;
                s.peakTemperature = 221.0;
                s.result = RoastingResult.BAKED;
                s.qualityScore = 30;
            }, BurnedTemperatureResultRule.RULE),
            rejected("C4: RAW con puntaje sobre 50", s -> {
                s.result = RoastingResult.RAW;
                s.qualityScore = 51;
            }, QualityScoreRule.RULE),
            rejected("C4: BURNED con puntaje bajo 5", s -> {
                s.result = RoastingResult.BURNED;
                s.qualityScore = 4;
            }, QualityScoreRule.RULE),
            rejected("C4: BAKED con puntaje 100", s -> {
                s.result = RoastingResult.BAKED;
                s.qualityScore = 100;
            }, QualityScoreRule.RULE),
            rejected("C5: PERFECT con puntaje negativo", s -> s.qualityScore = -1, QualityScoreRule.RULE),
            rejected("C5: PERFECT con puntaje sobre 100", s -> s.qualityScore = 101, QualityScoreRule.RULE)
        );
    }

    static Stream<Arguments> acceptedSessions() {
        return Stream.of(
            accepted("sesión normal", s -> { }),
            accepted("carga en 0 °C", s -> s.chargeTemperature = 0.0),
            accepted("carga en el máximo del aire (750 °C)", s -> s.chargeTemperature = 750.0),
            accepted("carga a 100 °C", s -> s.chargeTemperature = 100.0),
            accepted("carga a 264 °C", s -> s.chargeTemperature = 264.0),
            accepted("carga a 300 °C", s -> s.chargeTemperature = 300.0),
            accepted("objetivo en el mínimo", s -> s.targetTemperature = 150.0),
            accepted("objetivo en el máximo", s -> s.targetTemperature = 230.0),
            accepted("final en la temperatura segura", s -> {
                s.finalTemperature = 250.0;
                s.peakTemperature = 250.0;
                s.result = RoastingResult.BURNED;
                s.qualityScore = 5;
            }),
            accepted("pico igual a la final", s -> s.peakTemperature = 205.0),
            accepted("desarrollo igual al total", s -> s.developmentTimeSeconds = 780),
            accepted("sin tiempo de desarrollo (cliente antiguo)", s -> s.developmentTimeSeconds = null),
            accepted("desarrollo interrumpido: RAW con primer crack", s -> {
                s.finalTemperature = 184.55;
                s.peakTemperature = 200.54;
                s.result = RoastingResult.RAW;
                s.qualityScore = 46;
            }),
            accepted("quemado por tiempo con final bajo el techo", s -> {
                s.finalTemperature = 201.31;
                s.peakTemperature = 201.31;
                s.result = RoastingResult.BURNED;
                s.qualityScore = 26;
            }),
            accepted("RAW sin primer crack con final alta", s -> {
                s.firstCrackReached = false;
                s.finalTemperature = 240.0;
                s.peakTemperature = 240.0;
                s.result = RoastingResult.RAW;
                s.qualityScore = 33;
            }),
            accepted("PERFECT justo en el techo de crudo", s -> s.finalTemperature = 186.0),
            accepted("PERFECT justo en el techo de quemado", s -> {
                s.finalTemperature = 217.0;
                s.peakTemperature = 217.0;
            }),
            accepted("defecto con el puntaje mínimo", s -> {
                s.result = RoastingResult.BAKED;
                s.qualityScore = 5;
            }),
            accepted("defecto con el puntaje máximo", s -> {
                s.result = RoastingResult.BAKED;
                s.qualityScore = 50;
            }),
            accepted("PERFECT con puntaje 0", s -> s.qualityScore = 0),
            accepted("PERFECT con puntaje 100", s -> s.qualityScore = 100)
        );
    }

    private static Arguments rejected(String description, Consumer<Session> change, String rule) {
        return Arguments.of(description, change, rule);
    }

    private static Arguments accepted(String description, Consumer<Session> change) {
        return Arguments.of(description, change);
    }

    private static List<RoastSessionRule> allRules() {
        RoastValidationProperties properties = new RoastValidationProperties();
        MessageResolver messages = messages();
        return List.of(
            new ChargeTemperatureRule(properties, messages),
            new TargetTemperatureRule(properties, messages),
            new FinalTemperatureRule(properties, messages),
            new PeakTemperatureRule(messages),
            new DevelopmentTimeRule(messages),
            new FirstCrackResultRule(messages),
            new RawTemperatureResultRule(properties, messages),
            new BurnedTemperatureResultRule(properties, messages),
            new QualityScoreRule(properties, messages)
        );
    }

    private static MessageResolver messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new MessageResolver(source);
    }

    // Sesión PERFECT válida que cada caso modifica.
    static final class Session {
        Double chargeTemperature = 190.0;
        Double targetTemperature = 200.0;
        Integer totalDurationSeconds = 780;
        Double finalTemperature = 205.0;
        Double peakTemperature = 206.0;
        RoastingResult result = RoastingResult.PERFECT;
        Integer qualityScore = 75;
        Boolean firstCrackReached = true;
        Integer developmentTimeSeconds = 150;

        static Session valid() {
            return new Session();
        }

        SaveSessionRequest toRequest() {
            return new SaveSessionRequest(
                chargeTemperature,
                targetTemperature,
                totalDurationSeconds,
                finalTemperature,
                peakTemperature,
                result,
                qualityScore,
                firstCrackReached,
                developmentTimeSeconds
            );
        }
    }
}
