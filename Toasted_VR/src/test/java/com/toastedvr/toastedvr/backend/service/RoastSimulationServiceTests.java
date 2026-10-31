package com.toastedvr.toastedvr.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorClient;
import com.toastedvr.toastedvr.backend.client.RoastSimulatorUnavailableException;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.User;
import com.toastedvr.toastedvr.backend.dto.RoastTraceRequest;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.dto.SimulationDischargeResponse;
import com.toastedvr.toastedvr.backend.exception.ResourceNotFoundException;
import com.toastedvr.toastedvr.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("null")
class RoastSimulationServiceTests {

    private static final Long USER_ID = 7L;
    private static final UUID ID = UUID.fromString("c6c02b74-fa17-442f-b040-d9489cf3e94d");
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoastSimulatorClient client = mock(RoastSimulatorClient.class);
    private final RoastingSessionService roastingSessionService = mock(RoastingSessionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RoastSimulationService service = new RoastSimulationService(
        client, roastingSessionService, userRepository, objectMapper, mock(MessageResolver.class));

    private final User player = mock(User.class);
    private final SessionResultResponse saved = mock(SessionResultResponse.class);

    @BeforeEach
    void setUp() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(player));
        when(roastingSessionService.saveSession(eq(USER_ID), any())).thenReturn(saved);
    }

    @Test
    void savesTheSessionTheSimulatorScoredWithTheLevelStoredForThePlayer() throws Exception {
        List<RoastTraceRequest.Alert> alerts = List.of(new RoastTraceRequest.Alert("TEMP_STALL_DRYING", 90));
        when(player.getKnowledgeLevel()).thenReturn(KnowledgeLevel.ADVANCED);
        when(client.discharge(USER_ID, ID, KnowledgeLevel.ADVANCED, alerts)).thenReturn(objectMapper.readTree(DISCHARGE_REPLY));

        SimulationDischargeResponse response = service.discharge(USER_ID, ID, alerts);

        ArgumentCaptor<SaveSessionRequest> request = ArgumentCaptor.forClass(SaveSessionRequest.class);
        verify(roastingSessionService).saveSession(eq(USER_ID), request.capture());
        assertThat(request.getValue().result()).isEqualTo(RoastingResult.RAW);
        assertThat(request.getValue().qualityScore()).isEqualTo(27);
        assertThat(request.getValue().peakTemperature()).isEqualTo(103.24);
        assertThat(request.getValue().roastTrace().samples()).hasSize(3);
        assertThat(request.getValue().roastTrace().alerts()).containsExactly(new RoastTraceRequest.Alert("TEMP_STALL_DRYING", 90));
        assertThat(response.state().get("phase").asText()).isEqualTo("FINISHED");
        assertThat(response.session()).isSameAs(saved);
    }

    @Test
    void sendsNoAlertsWhenUnityOmitsThemAndNoLevelWhenThePlayerHasNone() throws Exception {
        when(client.discharge(USER_ID, ID, null, List.of())).thenReturn(objectMapper.readTree(DISCHARGE_REPLY));

        service.discharge(USER_ID, ID, null);

        verify(client).discharge(USER_ID, ID, null, List.of());
    }

    @Test
    void doesNotSaveAnythingWhenTheSimulatorReplyHasNoSession() throws Exception {
        JsonNode reply = objectMapper.readTree("{\"state\":{}}");
        when(client.discharge(any(), any(), any(), any())).thenReturn(reply);

        assertThatThrownBy(() -> service.discharge(USER_ID, ID, List.of()))
            .isInstanceOf(RoastSimulatorUnavailableException.class);
        verify(roastingSessionService, never()).saveSession(anyLong(), any());
    }

    @Test
    void rejectsADischargeOfAPlayerThatNoLongerExists() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discharge(USER_ID, ID, List.of())).isInstanceOf(ResourceNotFoundException.class);
        verify(client, never()).discharge(any(), any(), any(), any());
    }

    @Test
    void passesTheOtherActionsStraightToTheSimulator() {
        service.start(USER_ID, 195.0, null);
        service.read(USER_ID, ID, 12);
        service.setGas(USER_ID, ID, 60);
        service.charge(USER_ID, ID);
        service.abort(USER_ID, ID);

        verify(client).create(USER_ID, 195.0, null);
        verify(client).read(USER_ID, ID, 12);
        verify(client).setGas(USER_ID, ID, 60);
        verify(client).charge(USER_ID, ID);
        verify(client).remove(USER_ID, ID);
    }
}
