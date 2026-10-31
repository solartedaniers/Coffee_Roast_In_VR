package com.toastedvr.toastedvr.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

// El último estado del tueste ya descargado y la sesión guardada, con su nota y su evaluación.
public record SimulationDischargeResponse(JsonNode state, SessionResultResponse session) {
}
