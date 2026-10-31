package com.toastedvr.toastedvr.backend.controller;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorRejectedException;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorUnavailableException;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.SimulationDischargeRequest;
import com.toastedvr.toastedvr.backend.dto.SimulationDischargeResponse;
import com.toastedvr.toastedvr.backend.dto.SimulationGasRequest;
import com.toastedvr.toastedvr.backend.dto.SimulationStartRequest;
import com.toastedvr.toastedvr.backend.dto.VoiceAssistantErrorResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.RoastSimulationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// El tueste que Unity juega en el simulador de Node. El estado viaja tal como lo arma el simulador y
// todos los errores responden {"code", "message"}, igual que el asistente de voz y las alertas.
@RestController
@SuppressWarnings("null")
@RequestMapping("/api/v1/roasting/simulations")
public class RoastSimulationController {

    static final String INVALID_REQUEST = "SIMULATION_INVALID_REQUEST";
    static final String UNAVAILABLE = "SIMULATOR_UNAVAILABLE";

    private static final Map<String, String> MESSAGE_KEYS = Map.of(
        "SIMULATION_NOT_FOUND", "roasting.simulation.notFound",
        "SIMULATION_INVALID_PHASE", "roasting.simulation.invalidPhase",
        INVALID_REQUEST, "roasting.simulation.invalidRequest",
        "GAS_OUT_OF_RANGE", "roasting.simulation.gasOutOfRange"
    );

    private final RoastSimulationService simulationService;
    private final MessageResolver messages;

    public RoastSimulationController(RoastSimulationService simulationService, MessageResolver messages) {
        this.simulationService = simulationService;
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode start(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestBody(required = false) SimulationStartRequest request
    ) {
        SimulationStartRequest settings = request == null ? new SimulationStartRequest(null, null) : request;
        return simulationService.start(principal.getId(), settings.chargeTemperature(), settings.targetTemperature());
    }

    // Trae el estado actual y los ticks posteriores a afterTick, para dibujar la curva sin huecos.
    @GetMapping("/{id}")
    public JsonNode read(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @RequestParam(defaultValue = "0") long afterTick
    ) {
        return simulationService.read(principal.getId(), id, afterTick);
    }

    @PutMapping("/{id}/gas")
    public JsonNode setGas(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody SimulationGasRequest request
    ) {
        return simulationService.setGas(principal.getId(), id, request.gasPercent());
    }

    @PostMapping("/{id}/charge")
    public JsonNode charge(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return simulationService.charge(principal.getId(), id);
    }

    @PostMapping("/{id}/discharge")
    public SimulationDischargeResponse discharge(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id,
        @RequestBody(required = false) SimulationDischargeRequest request
    ) {
        return simulationService.discharge(principal.getId(), id, request == null ? null : request.alerts());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abort(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        simulationService.abort(principal.getId(), id);
    }

    // Un código que el backend no conoce es un error entre el backend y el simulador, no del jugador.
    @ExceptionHandler(RoastSimulatorRejectedException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleRejected(RoastSimulatorRejectedException exception) {
        String messageKey = MESSAGE_KEYS.get(exception.getCode());
        if (messageKey == null) {
            return handleUnavailable();
        }
        return error(HttpStatus.valueOf(exception.getStatus()), exception.getCode(), messageKey);
    }

    @ExceptionHandler(RoastSimulatorUnavailableException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "roasting.simulation.unavailable");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<VoiceAssistantErrorResponse> handleInvalidRequest() {
        return error(HttpStatus.BAD_REQUEST, INVALID_REQUEST, MESSAGE_KEYS.get(INVALID_REQUEST));
    }

    private ResponseEntity<VoiceAssistantErrorResponse> error(HttpStatus status, String code, String messageKey) {
        return ResponseEntity.status(status).body(new VoiceAssistantErrorResponse(code, messages.get(messageKey)));
    }
}
