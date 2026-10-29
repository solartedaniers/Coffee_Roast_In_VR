package com.toastedvr.toastedvr.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.toastedvr.toastedvr.backend.client.AiAlertPackageReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.VoiceAssistantErrorResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.AlertPackageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AlertPackageControllerTests {

    private static final String ETAG = "\"abc\"";
    private static final byte[] PACKAGE = "{\"level\":\"BEGINNER\"}".getBytes(StandardCharsets.UTF_8);

    private final AlertPackageService service = mock(AlertPackageService.class);
    private final MessageResolver messages = mock(MessageResolver.class);
    private final AlertPackageController controller = new AlertPackageController(service, messages);
    private final UserPrincipal principal = mock(UserPrincipal.class);

    @BeforeEach
    void setUp() {
        when(principal.getId()).thenReturn(7L);
        when(messages.get("roasting.assistant.alertsUnavailable")).thenReturn("No disponible.");
    }

    @Test
    void shouldReturnThePackageWithItsEtag() {
        when(service.fetch(7L, null)).thenReturn(new AiAlertPackageReply(200, ETAG, PACKAGE));

        ResponseEntity<?> response = controller.alerts(principal, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo(ETAG);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(PACKAGE);
    }

    @Test
    void shouldAnswerNotModifiedWithoutBody() {
        when(service.fetch(7L, ETAG)).thenReturn(new AiAlertPackageReply(304, ETAG, new byte[0]));

        ResponseEntity<?> response = controller.alerts(principal, ETAG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getETag()).isEqualTo(ETAG);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReportAnyOtherAiStatusAsUnavailable() {
        when(service.fetch(7L, null)).thenReturn(new AiAlertPackageReply(404, null, new byte[0]));

        ResponseEntity<?> response = controller.alerts(principal, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new VoiceAssistantErrorResponse("ALERTS_UNAVAILABLE", "No disponible."));
    }

    @Test
    void shouldReportTheAiServiceDownAsUnavailable() {
        ResponseEntity<VoiceAssistantErrorResponse> response = controller.handleAiServiceDown();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("ALERTS_UNAVAILABLE");
    }
}
