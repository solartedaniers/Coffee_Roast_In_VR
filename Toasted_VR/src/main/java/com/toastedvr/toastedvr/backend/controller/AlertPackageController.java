package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.client.AiAlertPackageReply;
import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.VoiceAssistantErrorResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.AlertPackageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Paquete de alertas proactivas que Unity descarga al empezar el tueste.
// Con If-None-Match responde 304 si Unity ya tiene esa versión.
@RestController
@SuppressWarnings("null")
@RequestMapping("/api/v1/roasting/assistant")
public class AlertPackageController {

    private static final String ALERTS_UNAVAILABLE = "ALERTS_UNAVAILABLE";

    private final AlertPackageService alertPackageService;
    private final MessageResolver messages;

    public AlertPackageController(AlertPackageService alertPackageService, MessageResolver messages) {
        this.alertPackageService = alertPackageService;
        this.messages = messages;
    }

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        AiAlertPackageReply reply = alertPackageService.fetch(principal.getId(), ifNoneMatch);
        if (reply.status() == HttpStatus.NOT_MODIFIED.value()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(reply.etag()).build();
        }
        if (reply.status() != HttpStatus.OK.value()) {
            return unavailable();
        }
        return ResponseEntity.ok().eTag(reply.etag()).contentType(MediaType.APPLICATION_JSON).body(reply.body());
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleAiServiceDown() {
        return unavailable();
    }

    // Unity sigue con el paquete que ya tiene o con el que trae el juego.
    private ResponseEntity<VoiceAssistantErrorResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new VoiceAssistantErrorResponse(ALERTS_UNAVAILABLE, messages.get("roasting.assistant.alertsUnavailable")));
    }
}
