package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.OllamaFeedbackService;
import com.toastedvr.toastedvr.backend.service.RoastingSessionService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roasting")
public class RoastingSessionController {

    private final RoastingSessionService roastingSessionService;
    private final OllamaFeedbackService ollamaFeedbackService;

    public RoastingSessionController(
        RoastingSessionService roastingSessionService,
        OllamaFeedbackService ollamaFeedbackService
    ) {
        this.roastingSessionService = roastingSessionService;
        this.ollamaFeedbackService = ollamaFeedbackService;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResultResponse saveSession(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SaveSessionRequest request
    ) {
        return roastingSessionService.saveSession(principal.getId(), request);
    }

    @GetMapping("/sessions/{id}/feedback")
    public Map<String, String> getFeedback(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        RoastingSession session = roastingSessionService.getOwnedSession(principal.getId(), id);
        String feedback = Objects.requireNonNullElse(ollamaFeedbackService.generateFeedback(session), "");
        return Map.of("feedback", feedback);
    }
}
