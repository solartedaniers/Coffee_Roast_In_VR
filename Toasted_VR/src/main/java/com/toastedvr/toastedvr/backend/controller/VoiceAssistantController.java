package com.toastedvr.toastedvr.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toastedvr.toastedvr.backend.client.AiServiceUnavailableException;
import com.toastedvr.toastedvr.backend.client.AiVoiceReply;
import com.toastedvr.toastedvr.backend.config.MessageResolver;
import com.toastedvr.toastedvr.backend.dto.VoiceAssistantErrorResponse;
import com.toastedvr.toastedvr.backend.dto.VoiceQuestionRequest;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.VoiceAssistantService;
import jakarta.validation.Validator;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// Pregunta por voz desde Unity: audio WAV y estado del tueste. El usuario y su
// nivel salen del token y de la base, nunca de lo que manda Unity.
@RestController
@SuppressWarnings("null")
@RequestMapping("/api/v1/roasting/assistant")
public class VoiceAssistantController {

    private static final String AUDIO_INVALID = "AUDIO_INVALID";
    private static final String AUDIO_TOO_LARGE = "AUDIO_TOO_LARGE";
    private static final String CONTEXT_INVALID = "CONTEXT_INVALID";
    private static final String ASSISTANT_UNAVAILABLE = "ASSISTANT_UNAVAILABLE";

    private final VoiceAssistantService voiceAssistantService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MessageResolver messages;

    public VoiceAssistantController(
        VoiceAssistantService voiceAssistantService,
        ObjectMapper objectMapper,
        Validator validator,
        MessageResolver messages
    ) {
        this.voiceAssistantService = voiceAssistantService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.messages = messages;
    }

    // El contexto llega como texto JSON para que Unity pueda mandarlo como campo
    // de formulario o como parte con cualquier tipo de contenido.
    @PostMapping(value = "/questions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> ask(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("audio") MultipartFile audio,
        @RequestParam("context") String context
    ) throws IOException {
        if (audio.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, AUDIO_INVALID, "roasting.assistant.audioInvalid");
        }
        VoiceQuestionRequest request = parseContext(context);
        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, CONTEXT_INVALID, "roasting.assistant.contextInvalid");
        }
        AiVoiceReply reply = voiceAssistantService.ask(principal.getId(), request, audio.getBytes());
        return ResponseEntity.status(reply.status()).contentType(MediaType.APPLICATION_JSON).body(reply.body());
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleAiServiceDown() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ASSISTANT_UNAVAILABLE, "roasting.assistant.unavailable");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, AUDIO_TOO_LARGE, "roasting.assistant.audioTooLarge");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<VoiceAssistantErrorResponse> handleMissingAudio() {
        return error(HttpStatus.BAD_REQUEST, AUDIO_INVALID, "roasting.assistant.audioInvalid");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<VoiceAssistantErrorResponse> handleMissingContext() {
        return error(HttpStatus.BAD_REQUEST, CONTEXT_INVALID, "roasting.assistant.contextInvalid");
    }

    private VoiceQuestionRequest parseContext(String context) {
        try {
            VoiceQuestionRequest request = objectMapper.readValue(context, VoiceQuestionRequest.class);
            return request != null && validator.validate(request).isEmpty() ? request : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ResponseEntity<VoiceAssistantErrorResponse> error(HttpStatus status, String code, String messageKey) {
        return ResponseEntity.status(status).body(new VoiceAssistantErrorResponse(code, messages.get(messageKey)));
    }
}
