package com.toastedvr.toastedvr.backend.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Trazas coherentes con una sesión (misma forma que arma RoastTraceRecorder.js): una muestra cada
// 5 s subiendo en línea recta hasta la temperatura final, gas al 30 % y los eventos del tueste.
final class RoastTracePayloads {

    static final int SAMPLE_INTERVAL_SECONDS = 5;
    private static final double START_TEMPERATURE = 100.0;
    private static final double GAS = 30.0;

    private RoastTracePayloads() {
    }

    static Map<String, Object> coherent(int totalSeconds, Integer firstCrackSecond, double finalTemperature, String mode) {
        double start = Math.min(START_TEMPERATURE, finalTemperature);
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int t = 0; t <= totalSeconds; t += SAMPLE_INTERVAL_SECONDS) {
            samples.add(sample(t, start + (finalTemperature - start) * t / totalSeconds));
        }
        if (totalSeconds % SAMPLE_INTERVAL_SECONDS != 0) {
            samples.add(sample(totalSeconds, finalTemperature));
        }
        List<Map<String, Object>> events = new ArrayList<>(List.of(Map.of("type", "CHARGE", "t", 0)));
        if (firstCrackSecond != null) {
            events.add(Map.of("type", "FIRST_CRACK", "t", firstCrackSecond));
        }
        events.add(Map.of("type", "DISCHARGE", "t", totalSeconds));
        Map<String, Object> trace = new HashMap<>();
        trace.put("version", 1);
        trace.put("operationMode", mode);
        trace.put("sampleIntervalSeconds", SAMPLE_INTERVAL_SECONDS);
        trace.put("samples", samples);
        trace.put("gasChanges", firstCrackSecond == null ? List.of() : List.of(Map.of("t", firstCrackSecond, "gas", 25.0)));
        trace.put("events", events);
        trace.put("alerts", List.of());
        return trace;
    }

    private static Map<String, Object> sample(int t, double bean) {
        return Map.of("t", t, "bean", Math.round(bean * 100) / 100.0, "gas", GAS);
    }
}
