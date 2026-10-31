package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorClient;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorRejectedException;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorUnavailableException;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.Role;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import com.toastedvr.toastedvr.backend.repository.BlacklistedTokenRepository;
import com.toastedvr.toastedvr.backend.repository.RoastEvaluationRepository;
import com.toastedvr.toastedvr.backend.repository.RoastSessionTraceRepository;
import com.toastedvr.toastedvr.backend.repository.RoastingSessionRepository;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import com.toastedvr.toastedvr.backend.security.JwtService;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@SuppressWarnings("null")
@AutoConfigureMockMvc
class RoastSimulationControllerIntegrationTests {

    private static final String SIMULATIONS = "/api/v1/roasting/simulations";
    private static final UUID ID = UUID.fromString("c6c02b74-fa17-442f-b040-d9489cf3e94d");
    private static final String SIMULATION = SIMULATIONS + "/" + ID;
    private static final String STATE = "{\"state\":{\"tick\":3,\"phase\":\"PREHEAT\"},\"ticks\":[]}";
    // Respuesta real del simulador al descargar (la traza recortada a tres muestras).
    private static final String DISCHARGE_REPLY = """
        {"state":{"tick":420,"phase":"FINISHED"},
         "sessionRequest":{"chargeTemperature":190.04,"targetTemperature":210,"totalDurationSeconds":136,
           "finalTemperature":103.24,"peakTemperature":103.24,"result":"RAW","qualityScore":27,
           "firstCrackReached":false,"developmentTimeSeconds":0,
           "roastTrace":{"version":1,"operationMode":"MANUAL","sampleIntervalSeconds":5,
             "samples":[{"t":1,"bean":187.9,"gas":50},{"t":5,"bean":179.71,"gas":50},{"t":136,"bean":103.24,"gas":50}],
             "gasChanges":[],"events":[{"type":"CHARGE","t":0},{"type":"DISCHARGE","t":136}],
             "alerts":[{"id":"TEMP_STALL_DRYING","t":90}]}}}
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoastingSessionRepository roastingSessionRepository;

    @Autowired
    private RoastEvaluationRepository roastEvaluationRepository;

    @Autowired
    private RoastSessionTraceRepository roastSessionTraceRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private RoastSimulatorClient simulatorClient;

    private User player;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        player = new User("Player", "simulator@toastedvr.test", "simPlayer", passwordEncoder.encode("Password123!"));
        player.markEmailAsVerified();
        player.assignRole(Role.PLAYER);
        player.updateKnowledgeLevel(KnowledgeLevel.BEGINNER);
        player = userRepository.save(Objects.requireNonNull(player));
    }

    @AfterEach
    void cleanDatabase() {
        roastEvaluationRepository.deleteAll();
        roastSessionTraceRepository.deleteAll();
        roastingSessionRepository.deleteAll();
        blacklistedTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void startsTheSimulationOfTheAuthenticatedPlayer() throws Exception {
        when(simulatorClient.create(player.getId(), 195.0, null))
            .thenReturn(objectMapper.readTree("{\"simulationId\":\"" + ID + "\",\"state\":{\"tick\":0}}"));

        mockMvc.perform(post(SIMULATIONS).header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"chargeTemperature\":195}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.simulationId").value(ID.toString()));
    }

    @Test
    void startsWithTheDefaultsWhenUnitySendsNoBody() throws Exception {
        when(simulatorClient.create(player.getId(), null, null)).thenReturn(objectMapper.readTree("{\"simulationId\":\"x\"}"));

        mockMvc.perform(post(SIMULATIONS).header("Authorization", bearer())).andExpect(status().isCreated());
        verify(simulatorClient).create(player.getId(), null, null);
    }

    @Test
    void passesTheStateAndTheTicksThrough() throws Exception {
        when(simulatorClient.read(player.getId(), ID, 12)).thenReturn(objectMapper.readTree(STATE));

        mockMvc.perform(get(SIMULATION).param("afterTick", "12").header("Authorization", bearer()))
            .andExpect(status().isOk())
            .andExpect(content().json(STATE, true));
    }

    @Test
    void changesTheGasChargesAndAborts() throws Exception {
        when(simulatorClient.setGas(player.getId(), ID, 65.0)).thenReturn(objectMapper.readTree(STATE));
        when(simulatorClient.charge(player.getId(), ID)).thenReturn(objectMapper.readTree(STATE));

        mockMvc.perform(put(SIMULATION + "/gas").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"gasPercent\":65}"))
            .andExpect(status().isOk());
        mockMvc.perform(post(SIMULATION + "/charge").header("Authorization", bearer())).andExpect(status().isOk());
        mockMvc.perform(delete(SIMULATION).header("Authorization", bearer())).andExpect(status().isNoContent());
        verify(simulatorClient).remove(player.getId(), ID);
    }

    @Test
    void savesTheSessionOnDischargeWithTheAlertsAndTheScoreOfTheSimulator() throws Exception {
        List<RoastTraceRequest.Alert> alerts = List.of(new RoastTraceRequest.Alert("TEMP_STALL_DRYING", 90));
        when(simulatorClient.discharge(player.getId(), ID, KnowledgeLevel.BEGINNER, alerts))
            .thenReturn(objectMapper.readTree(DISCHARGE_REPLY));

        mockMvc.perform(post(SIMULATION + "/discharge").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"alerts\":[{\"id\":\"TEMP_STALL_DRYING\",\"t\":90}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state.phase").value("FINISHED"))
            .andExpect(jsonPath("$.session.qualityScore").value(27))
            .andExpect(jsonPath("$.session.result").value("RAW"))
            .andExpect(jsonPath("$.session.evaluation.status").exists());

        List<RoastingSession> sessions = roastingSessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getQualityScore()).isEqualTo(27);
        assertThat(sessions.get(0).getKnowledgeLevel()).isEqualTo(KnowledgeLevel.BEGINNER);
        assertThat(roastSessionTraceRepository.count()).isEqualTo(1);
    }

    @Test
    void answersTheSimulatorErrorsWithCodeAndMessageInSpanish() throws Exception {
        when(simulatorClient.charge(anyLong(), any())).thenThrow(new RoastSimulatorRejectedException(409, "SIMULATION_INVALID_PHASE"));
        when(simulatorClient.read(anyLong(), any(), anyLong())).thenThrow(new RoastSimulatorRejectedException(404, "SIMULATION_NOT_FOUND"));
        when(simulatorClient.setGas(anyLong(), any(), anyDouble())).thenThrow(new RoastSimulatorRejectedException(400, "GAS_OUT_OF_RANGE"));

        mockMvc.perform(post(SIMULATION + "/charge").header("Authorization", bearer()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SIMULATION_INVALID_PHASE"))
            .andExpect(jsonPath("$.message").value("Esa acción no se puede hacer en este momento del tueste."));
        mockMvc.perform(get(SIMULATION).header("Authorization", bearer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("SIMULATION_NOT_FOUND"));
        mockMvc.perform(put(SIMULATION + "/gas").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"gasPercent\":150}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GAS_OUT_OF_RANGE"))
            .andExpect(jsonPath("$.message").value("El gas debe estar entre 0 y 100 %."));
    }

    @Test
    void answersUnavailableWhenTheSimulatorIsDownOrReturnsAnUnknownCode() throws Exception {
        when(simulatorClient.charge(anyLong(), any())).thenThrow(new RoastSimulatorUnavailableException("caído"));
        when(simulatorClient.read(anyLong(), any(), anyLong())).thenThrow(new RoastSimulatorRejectedException(404, "ROUTE_NOT_FOUND"));

        mockMvc.perform(post(SIMULATION + "/charge").header("Authorization", bearer()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SIMULATOR_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(get(SIMULATION).header("Authorization", bearer()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("SIMULATOR_UNAVAILABLE"));
    }

    @Test
    void rejectsMalformedRequestsWithoutCallingTheSimulator() throws Exception {
        mockMvc.perform(get(SIMULATIONS + "/not-a-uuid").header("Authorization", bearer()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SIMULATION_INVALID_REQUEST"));
        mockMvc.perform(put(SIMULATION + "/gas").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SIMULATION_INVALID_REQUEST"));
        mockMvc.perform(put(SIMULATION + "/gas").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{roto"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SIMULATION_INVALID_REQUEST"));

        verify(simulatorClient, never()).read(anyLong(), any(), anyLong());
        verify(simulatorClient, never()).setGas(anyLong(), any(), anyDouble());
        verify(simulatorClient, never()).discharge(anyLong(), eq(ID), any(), any());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(player));
    }
}
