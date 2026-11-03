package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.VoiceAssistantErrorResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.FeedbackSpeechService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Unity lo pide al terminar el tueste, sin cuerpo: el texto se arma aquí desde la sesión del jugador.
// La respuesta del servicio IA (audio o {"code", "message"}) pasa tal cual, igual que la pregunta por voz.
@RestController
@SuppressWarnings("null")
@RequestMapping("/api/v1/roasting/sessions")
public class FeedbackSpeechController {

    static final String SPEECH_UNAVAILABLE = "SPEECH_UNAVAILABLE";

    private final FeedbackSpeechService feedbackSpeechService;
    private final MessageResolver messages;

    public FeedbackSpeechController(FeedbackSpeechService feedbackSpeechService, MessageResolver messages) {
        this.feedbackSpeechService = feedbackSpeechService;
        this.messages = messages;
    }

    @PostMapping("/{id}/feedback/speech")
    public ResponseEntity<byte[]> speak(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        AiVoiceReply reply = feedbackSpeechService.speak(principal.getId(), id);
        return ResponseEntity.status(reply.status()).contentType(MediaType.APPLICATION_JSON).body(reply.body());
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleAiServiceDown() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            new VoiceAssistantErrorResponse(SPEECH_UNAVAILABLE, messages.get("roasting.feedback.speech.unavailable")));
    }
}
