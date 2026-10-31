package com.toastedvr.toastedvr.backend.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorClient;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorUnavailableException;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.dto.SimulationDischargeResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

// El tueste que Unity juega en el simulador. Al descargar, el nivel sale de la base (Unity no lo elige)
// y la sesión se guarda con la nota que calculó el simulador, por el mismo camino que la web.
@Service
public class RoastSimulationService {

    private static final String SESSION_REQUEST_FIELD = "sessionRequest";
    private static final String STATE_FIELD = "state";

    private final RoastSimulatorClient simulatorClient;
    private final RoastingSessionService roastingSessionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final MessageResolver messages;

    public RoastSimulationService(
        RoastSimulatorClient simulatorClient,
        RoastingSessionService roastingSessionService,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        MessageResolver messages
    ) {
        this.simulatorClient = simulatorClient;
        this.roastingSessionService = roastingSessionService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    public JsonNode start(Long userId, Double chargeTemperature, Double targetTemperature) {
        return simulatorClient.create(userId, chargeTemperature, targetTemperature);
    }

    public JsonNode read(Long userId, UUID simulationId, long afterTick) {
        return simulatorClient.read(userId, simulationId, afterTick);
    }

    public JsonNode setGas(Long userId, UUID simulationId, double gasPercent) {
        return simulatorClient.setGas(userId, simulationId, gasPercent);
    }

    public JsonNode charge(Long userId, UUID simulationId) {
        return simulatorClient.charge(userId, simulationId);
    }

    public void abort(Long userId, UUID simulationId) {
        simulatorClient.remove(userId, simulationId);
    }

    public SimulationDischargeResponse discharge(Long userId, UUID simulationId, List<RoastTraceRequest.Alert> alerts) {
        User user = userRepository.findById(Objects.requireNonNull(userId))
            .orElseThrow(() -> new ResourceNotFoundException(messages.get("user.notFound")));
        JsonNode reply = simulatorClient.discharge(
            userId,
            simulationId,
            user.getKnowledgeLevel(),
            Objects.requireNonNullElse(alerts, List.of())
        );
        SessionResultResponse session = roastingSessionService.saveSession(userId, toSessionRequest(reply));
        return new SimulationDischargeResponse(reply.get(STATE_FIELD), session);
    }

    private SaveSessionRequest toSessionRequest(JsonNode reply) {
        JsonNode sessionRequest = reply.get(SESSION_REQUEST_FIELD);
        if (sessionRequest == null || !sessionRequest.isObject()) {
            throw new RoastSimulatorUnavailableException("El simulador no devolvió la sesión al descargar");
        }
        try {
            return objectMapper.treeToValue(sessionRequest, SaveSessionRequest.class);
        } catch (JsonProcessingException e) {
            throw new RoastSimulatorUnavailableException("El simulador devolvió una sesión que no se puede leer", e);
        }
    }
}
