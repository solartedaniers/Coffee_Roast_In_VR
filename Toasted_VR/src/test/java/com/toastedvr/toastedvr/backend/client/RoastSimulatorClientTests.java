package com.toastedvr.toastedvr.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("null")
class RoastSimulatorClientTests {

    private static final String BASE = "http://simulator.test/api/v1/simulations";
    private static final UUID ID = UUID.fromString("c6c02b74-fa17-442f-b040-d9489cf3e94d");
    private static final Long USER_ID = 7L;
    private static final String STATE = "{\"state\":{\"tick\":3,\"phase\":\"PREHEAT\"}}";

    private MockRestServiceServer simulator;
    private RoastSimulatorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://simulator.test");
        simulator = MockRestServiceServer.bindTo(builder).build();
        client = new RoastSimulatorClient(builder.build(), new ObjectMapper());
    }

    @Test
    void createsTheSimulationForThePlayer() {
        simulator.expect(requestTo(BASE))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(RoastSimulatorClient.USER_HEADER, "7"))
            .andExpect(content().json("{\"chargeTemperature\":195.0,\"targetTemperature\":null}"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"simulationId\":\"" + ID + "\",\"state\":{\"tick\":0}}"));

        JsonNode body = client.create(USER_ID, 195.0, null);

        assertThat(body.get("simulationId").asText()).isEqualTo(ID.toString());
        simulator.verify();
    }

    @Test
    void readsTheTicksAfterTheOneTheClientAlreadyHas() {
        simulator.expect(requestTo(BASE + "/" + ID + "?afterTick=12"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(RoastSimulatorClient.USER_HEADER, "7"))
            .andRespond(withSuccess(STATE, MediaType.APPLICATION_JSON));

        assertThat(client.read(USER_ID, ID, 12).at("/state/tick").asInt()).isEqualTo(3);
        simulator.verify();
    }

    @Test
    void sendsTheGasChargeAndRemoval() {
        simulator.expect(requestTo(BASE + "/" + ID + "/gas"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().json("{\"gasPercent\":65.0}"))
            .andRespond(withSuccess(STATE, MediaType.APPLICATION_JSON));
        simulator.expect(requestTo(BASE + "/" + ID + "/charge"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(STATE, MediaType.APPLICATION_JSON));
        simulator.expect(requestTo(BASE + "/" + ID))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.setGas(USER_ID, ID, 65.0);
        client.charge(USER_ID, ID);
        client.remove(USER_ID, ID);
        simulator.verify();
    }

    @Test
    void sendsTheLevelAndTheAlertsThatSoundedOnDischarge() {
        simulator.expect(requestTo(BASE + "/" + ID + "/discharge"))
            .andExpect(content().json("{\"knowledgeLevel\":\"ADVANCED\",\"alerts\":[{\"id\":\"FIRST_CRACK\",\"t\":625}]}"))
            .andRespond(withSuccess("{\"state\":{},\"sessionRequest\":{\"qualityScore\":80}}", MediaType.APPLICATION_JSON));

        JsonNode body = client.discharge(USER_ID, ID, KnowledgeLevel.ADVANCED, List.of(new RoastTraceRequest.Alert("FIRST_CRACK", 625)));

        assertThat(body.at("/sessionRequest/qualityScore").asInt()).isEqualTo(80);
        simulator.verify();
    }

    @Test
    void reportsTheCodeWhenTheSimulatorRejectsTheRequest() {
        simulator.expect(requestTo(BASE + "/" + ID + "/charge"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"SIMULATION_INVALID_PHASE\"}"));

        assertThatThrownBy(() -> client.charge(USER_ID, ID))
            .isInstanceOfSatisfying(RoastSimulatorRejectedException.class, e -> {
                assertThat(e.getStatus()).isEqualTo(409);
                assertThat(e.getCode()).isEqualTo("SIMULATION_INVALID_PHASE");
            });
    }

    @Test
    void reportsTheSimulatorAsUnavailableWhenItFailsOrCannotBeReached() {
        simulator.expect(requestTo(BASE + "/" + ID + "/charge")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        simulator.expect(requestTo(BASE + "/" + ID + "/charge")).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("no es json"));
        simulator.expect(requestTo(BASE + "/" + ID + "/charge")).andRespond(withException(new ConnectException("refused")));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> client.charge(USER_ID, ID)).isInstanceOf(RoastSimulatorUnavailableException.class);
        }
        simulator.verify();
    }
}
