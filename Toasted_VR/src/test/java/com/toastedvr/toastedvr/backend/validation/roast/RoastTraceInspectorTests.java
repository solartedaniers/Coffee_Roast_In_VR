package com.toastedvr.toastedvr.backend.validation.roast;

import static org.assertj.core.api.Assertions.assertThat;

import com.toastedvr.toastedvr.backend.config.RoastTraceProperties;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RoastTraceInspectorTests {

    private final RoastTraceInspector inspector = new RoastTraceInspector(new RoastTraceProperties());

    // Tueste de 780 s con primer crack a los 630 s (desarrollo de 150 s) y pico de 206 °C.
    private static RoastTraceRequest trace(UnaryOperator<List<RoastTraceRequest.Sample>> samples) {
        List<RoastTraceRequest.Sample> base = new ArrayList<>(IntStream.rangeClosed(0, 156)
            .mapToObj(index -> new RoastTraceRequest.Sample(index * 5, 100.0 + index * 0.66, 30.0)).toList());
        return new RoastTraceRequest(1, "MANUAL", 5, samples.apply(base),
            List.of(new RoastTraceRequest.GasChange(630, 25.0)),
            List.of(new RoastTraceRequest.Event("CHARGE", 0), new RoastTraceRequest.Event("FIRST_CRACK", 630),
                new RoastTraceRequest.Event("DISCHARGE", 780)),
            List.of(new RoastTraceRequest.Alert("DEVELOPMENT_READY", 770)));
    }

    private static SaveSessionRequest session(RoastTraceRequest trace) {
        return new SaveSessionRequest(190.0, 200.0, 780, 203.0, 206.0, RoastingResult.PERFECT, 74, true, 150, trace);
    }

    private Optional<RoastTraceProblem> problemOf(RoastTraceRequest trace) {
        return inspector.findProblem(session(trace));
    }

    private static RoastTraceRequest with(RoastTraceRequest trace, Integer version, String mode,
                                          List<RoastTraceRequest.GasChange> gas, List<RoastTraceRequest.Event> events,
                                          List<RoastTraceRequest.Alert> alerts) {
        return new RoastTraceRequest(version, mode, trace.sampleIntervalSeconds(), trace.samples(), gas, events, alerts);
    }

    @Test
    void acceptsACoherentTrace() {
        assertThat(problemOf(trace(samples -> samples))).isEmpty();
    }

    @Test
    void acceptsATraceWithoutOptionalLists() {
        RoastTraceRequest base = trace(samples -> samples);
        RoastTraceRequest trace = with(base, 1, "AUTO", null,
            List.of(new RoastTraceRequest.Event("FIRST_CRACK", 630)), null);

        assertThat(problemOf(trace)).isEmpty();
    }

    @Test
    void rejectsAnUnknownVersionOrMode() {
        RoastTraceRequest base = trace(samples -> samples);

        assertThat(problemOf(with(base, 2, "MANUAL", base.gasChanges(), base.events(), base.alerts())))
            .contains(RoastTraceProblem.UNSUPPORTED_VERSION);
        assertThat(problemOf(with(base, 1, "REMOTE", base.gasChanges(), base.events(), base.alerts())))
            .contains(RoastTraceProblem.UNKNOWN_OPERATION_MODE);
    }

    @Test
    void rejectsATraceLargerThanTheLimits() {
        RoastTraceRequest base = trace(samples -> samples);
        List<RoastTraceRequest.Alert> alerts = IntStream.range(0, 61)
            .mapToObj(index -> new RoastTraceRequest.Alert("BURN_WARNING", index)).toList();

        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(), base.events(), alerts)))
            .contains(RoastTraceProblem.TOO_LARGE);
    }

    @Test
    void rejectsSamplesOutOfOrderOrOutsideTheSession() {
        assertThat(problemOf(trace(samples -> {
            samples.set(3, new RoastTraceRequest.Sample(5, 101.0, 30.0));
            return samples;
        }))).contains(RoastTraceProblem.INVALID_SAMPLES);
        assertThat(problemOf(trace(samples -> {
            samples.add(new RoastTraceRequest.Sample(800, 205.0, 30.0));
            return samples;
        }))).contains(RoastTraceProblem.INVALID_SAMPLES);
        assertThat(problemOf(trace(samples -> {
            samples.set(4, new RoastTraceRequest.Sample(20, 110.0, 130.0));
            return samples;
        }))).contains(RoastTraceProblem.INVALID_SAMPLES);
    }

    @Test
    void rejectsInvalidGasChangesEventsAndAlerts() {
        RoastTraceRequest base = trace(samples -> samples);

        assertThat(problemOf(with(base, 1, "MANUAL",
            List.of(new RoastTraceRequest.GasChange(700, 20.0), new RoastTraceRequest.GasChange(600, 25.0)),
            base.events(), base.alerts()))).contains(RoastTraceProblem.INVALID_GAS_CHANGES);
        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(),
            List.of(new RoastTraceRequest.Event("FIRST_CRACK", 630), new RoastTraceRequest.Event("FIRST_CRACK", 640)),
            base.alerts()))).contains(RoastTraceProblem.INVALID_EVENTS);
        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(), base.events(),
            List.of(new RoastTraceRequest.Alert(" ", 10))))).contains(RoastTraceProblem.INVALID_ALERTS);
    }

    @Test
    void rejectsATraceHotterThanTheSessionPeak() {
        assertThat(problemOf(trace(samples -> {
            samples.set(100, new RoastTraceRequest.Sample(500, 210.0, 30.0));
            return samples;
        }))).contains(RoastTraceProblem.PEAK_MISMATCH);
    }

    @Test
    void ignoresTheChargeReadingBeforeTheTurningPoint() {
        RoastTraceRequest charged = trace(samples -> {
            samples.set(0, new RoastTraceRequest.Sample(0, 215.0, 30.0));
            samples.set(1, new RoastTraceRequest.Sample(5, 160.0, 30.0));
            return samples;
        });

        assertThat(problemOf(charged)).isEmpty();
    }

    @Test
    void rejectsAFirstCrackOrDischargeThatDoesNotMatchTheSession() {
        RoastTraceRequest base = trace(samples -> samples);

        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(),
            List.of(new RoastTraceRequest.Event("FIRST_CRACK", 600)), base.alerts())))
            .contains(RoastTraceProblem.FIRST_CRACK_MISMATCH);
        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(), List.of(), base.alerts())))
            .contains(RoastTraceProblem.FIRST_CRACK_MISMATCH);
        assertThat(problemOf(with(base, 1, "MANUAL", base.gasChanges(),
            List.of(new RoastTraceRequest.Event("FIRST_CRACK", 630), new RoastTraceRequest.Event("DISCHARGE", 700)),
            base.alerts()))).contains(RoastTraceProblem.DISCHARGE_MISMATCH);
    }
}
