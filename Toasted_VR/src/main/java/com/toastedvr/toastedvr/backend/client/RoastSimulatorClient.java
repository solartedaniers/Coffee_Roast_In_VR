package com.toastedvr.toastedvr.backend.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Habla con el simulador de tueste en Node (carpeta roast-simulator/). El jugador viaja en X-User-Id:
// el simulador confía en ese encabezado porque solo el backend, que ya validó el token, lo llama.
@Component
@SuppressWarnings("null")
public class RoastSimulatorClient {

    static final String USER_HEADER = "X-User-Id";
    private static final String SIMULATIONS = "/api/v1/simulations";
    private static final String SIMULATION = SIMULATIONS + "/{id}";
    private static final String CODE_FIELD = "code";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RoastSimulatorClient(@Qualifier("roastSimulatorRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode create(Long userId, Double chargeTemperature, Double targetTemperature) {
        return send(restClient.post()
            .uri(SIMULATIONS)
            .header(USER_HEADER, userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new StartBody(chargeTemperature, targetTemperature)));
    }

    public JsonNode read(Long userId, UUID simulationId, long afterTick) {
        return send(restClient.get()
            .uri(builder -> builder.path(SIMULATION).queryParam("afterTick", afterTick).build(simulationId))
            .header(USER_HEADER, userId.toString()));
    }

    public JsonNode setGas(Long userId, UUID simulationId, double gasPercent) {
        return send(restClient.put()
            .uri(SIMULATION + "/gas", simulationId)
            .header(USER_HEADER, userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("gasPercent", gasPercent)));
    }

    public JsonNode charge(Long userId, UUID simulationId) {
        return send(restClient.post()
            .uri(SIMULATION + "/charge", simulationId)
            .header(USER_HEADER, userId.toString()));
    }

    // Sin nivel, el simulador califica como INTERMEDIATE, igual que la web.
    public JsonNode discharge(Long userId, UUID simulationId, KnowledgeLevel level, List<RoastTraceRequest.Alert> alerts) {
        return send(restClient.post()
            .uri(SIMULATION + "/discharge", simulationId)
            .header(USER_HEADER, userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DischargeBody(level, alerts)));
    }

    public void remove(Long userId, UUID simulationId) {
        send(restClient.delete()
            .uri(SIMULATION, simulationId)
            .header(USER_HEADER, userId.toString()));
    }

    private JsonNode send(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange((ignored, response) -> toBody(response.getStatusCode().value(), response.getBody().readAllBytes()));
        } catch (RestClientException e) {
            throw new RoastSimulatorUnavailableException("El simulador de tueste no respondió", e);
        }
    }

    private JsonNode toBody(int status, byte[] body) throws IOException {
        if (status >= 200 && status < 300) {
            return body.length == 0 ? objectMapper.nullNode() : objectMapper.readTree(body);
        }
        if (status >= 400 && status < 500 && body.length > 0) {
            JsonNode code = objectMapper.readTree(body).get(CODE_FIELD);
            if (code != null && code.isTextual()) {
                throw new RoastSimulatorRejectedException(status, code.asText());
            }
        }
        throw new RoastSimulatorUnavailableException("El simulador de tueste respondió " + status);
    }

    private record StartBody(Double chargeTemperature, Double targetTemperature) {
    }

    private record DischargeBody(KnowledgeLevel knowledgeLevel, List<RoastTraceRequest.Alert> alerts) {
    }
}
