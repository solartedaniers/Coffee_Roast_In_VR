package com.toastedvr.toastedvr.backend.validation.roast;

import com.toastedvr.toastedvr.backend.config.RoastTraceProperties;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

// Revisa que la traza sea del tamaño permitido y coherente con la sesión que la acompaña. Solo
// informa el problema: quien llama descarta la traza y la sesión se guarda igual.
@Component
public class RoastTraceInspector {

    static final Set<String> OPERATION_MODES = Set.of("MANUAL", "AUTO");
    static final String FIRST_CRACK = "FIRST_CRACK";
    static final String DISCHARGE = "DISCHARGE";
    private static final Set<String> EVENT_TYPES = Set.of("CHARGE", FIRST_CRACK, "SECOND_CRACK", DISCHARGE);
    private static final double GAS_MIN = 0;
    private static final double GAS_MAX = 100;

    private final RoastTraceProperties properties;

    public RoastTraceInspector(RoastTraceProperties properties) {
        this.properties = properties;
    }

    public Optional<RoastTraceProblem> findProblem(SaveSessionRequest session) {
        RoastTraceRequest trace = Objects.requireNonNull(session.roastTrace());
        int lastSecond = session.totalDurationSeconds() + properties.getTimeToleranceSeconds();
        if (!Objects.equals(trace.version(), properties.getSupportedVersion())) {
            return Optional.of(RoastTraceProblem.UNSUPPORTED_VERSION);
        }
        if (!OPERATION_MODES.contains(trace.operationMode())) {
            return Optional.of(RoastTraceProblem.UNKNOWN_OPERATION_MODE);
        }
        if (size(trace.samples()) > properties.getMaxSamples()
            || size(trace.gasChanges()) > properties.getMaxGasChanges()
            || size(trace.events()) > properties.getMaxEvents()
            || size(trace.alerts()) > properties.getMaxAlerts()) {
            return Optional.of(RoastTraceProblem.TOO_LARGE);
        }
        if (!validSamples(trace, lastSecond)) {
            return Optional.of(RoastTraceProblem.INVALID_SAMPLES);
        }
        if (!validGasChanges(trace.gasChanges(), lastSecond)) {
            return Optional.of(RoastTraceProblem.INVALID_GAS_CHANGES);
        }
        if (!validEvents(trace.events(), lastSecond)) {
            return Optional.of(RoastTraceProblem.INVALID_EVENTS);
        }
        if (!validAlerts(trace.alerts(), lastSecond)) {
            return Optional.of(RoastTraceProblem.INVALID_ALERTS);
        }
        return findMismatch(session, trace);
    }

    private Optional<RoastTraceProblem> findMismatch(SaveSessionRequest session, RoastTraceRequest trace) {
        double hottest = trace.samples().stream().mapToDouble(RoastTraceRequest.Sample::bean).max().orElseThrow();
        if (hottest > session.peakTemperature() + properties.getTemperatureTolerance()) {
            return Optional.of(RoastTraceProblem.PEAK_MISMATCH);
        }
        Optional<Integer> firstCrack = eventSecond(trace, FIRST_CRACK);
        if (firstCrack.isPresent() != session.firstCrackReached()) {
            return Optional.of(RoastTraceProblem.FIRST_CRACK_MISMATCH);
        }
        Integer development = session.developmentTimeSeconds();
        if (firstCrack.isPresent() && development != null
            && Math.abs(session.totalDurationSeconds() - firstCrack.get() - development) > properties.getTimeToleranceSeconds()) {
            return Optional.of(RoastTraceProblem.FIRST_CRACK_MISMATCH);
        }
        Optional<Integer> discharge = eventSecond(trace, DISCHARGE);
        if (discharge.isPresent()
            && Math.abs(discharge.get() - session.totalDurationSeconds()) > properties.getTimeToleranceSeconds()) {
            return Optional.of(RoastTraceProblem.DISCHARGE_MISMATCH);
        }
        return Optional.empty();
    }

    private boolean validSamples(RoastTraceRequest trace, int lastSecond) {
        List<RoastTraceRequest.Sample> samples = trace.samples();
        if (trace.sampleIntervalSeconds() == null || trace.sampleIntervalSeconds() <= 0 || samples == null || samples.size() < 2) {
            return false;
        }
        int previous = -1;
        for (RoastTraceRequest.Sample sample : samples) {
            if (sample == null || !validSecond(sample.t(), lastSecond) || sample.t() <= previous
                || sample.bean() == null || !Double.isFinite(sample.bean()) || !validGas(sample.gas())) {
                return false;
            }
            previous = sample.t();
        }
        return true;
    }

    private boolean validGasChanges(List<RoastTraceRequest.GasChange> changes, int lastSecond) {
        int previous = 0;
        for (RoastTraceRequest.GasChange change : listOrEmpty(changes)) {
            if (change == null || !validSecond(change.t(), lastSecond) || change.t() < previous || !validGas(change.gas())) {
                return false;
            }
            previous = change.t();
        }
        return true;
    }

    private boolean validEvents(List<RoastTraceRequest.Event> events, int lastSecond) {
        List<RoastTraceRequest.Event> list = listOrEmpty(events);
        long distinctTypes = list.stream().filter(Objects::nonNull).map(RoastTraceRequest.Event::type).distinct().count();
        return distinctTypes == list.size() && list.stream()
            .allMatch(event -> event != null && EVENT_TYPES.contains(event.type()) && validSecond(event.t(), lastSecond));
    }

    private boolean validAlerts(List<RoastTraceRequest.Alert> alerts, int lastSecond) {
        return listOrEmpty(alerts).stream().allMatch(alert -> alert != null && alert.id() != null && !alert.id().isBlank()
            && alert.id().length() <= properties.getMaxAlertIdLength() && validSecond(alert.t(), lastSecond));
    }

    private static Optional<Integer> eventSecond(RoastTraceRequest trace, String type) {
        return listOrEmpty(trace.events()).stream().filter(event -> type.equals(event.type()))
            .map(RoastTraceRequest.Event::t).findFirst();
    }

    private static boolean validSecond(Integer second, int lastSecond) {
        return second != null && second >= 0 && second <= lastSecond;
    }

    private static boolean validGas(Double gas) {
        return gas != null && gas >= GAS_MIN && gas <= GAS_MAX;
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static <T> List<T> listOrEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
