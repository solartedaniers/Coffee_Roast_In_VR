package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.domain.RoastingResult;
import com.toastedvr.toastedvr.backend.domain.RoastingSession;
import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionHistoryItemResponse;
import com.toastedvr.toastedvr.backend.dto.SessionHistorySummaryResponse;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.OllamaFeedbackService;
import com.toastedvr.toastedvr.backend.service.RoastingSessionService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // RF016: historial del jugador autenticado, 10 por página (configurable).
    @GetMapping("/sessions")
    public Page<SessionHistoryItemResponse> getHistory(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) RoastingResult result
    ) {
        return roastingSessionService.getHistory(principal.getId(), page, result);
    }

    @GetMapping("/sessions/summary")
    public SessionHistorySummaryResponse getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        return roastingSessionService.getSummary(principal.getId());
    }

    // Una sesión de otro jugador responde 404, igual que una que no existe.
    @GetMapping("/sessions/{id}")
    public SessionResultResponse getSessionDetail(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        return roastingSessionService.getSessionDetail(principal.getId(), id);
    }

    @GetMapping("/sessions/{id}/feedback")
    public Map<String, String> getFeedback(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        RoastingSession session = roastingSessionService.getOwnedSession(principal.getId(), id);
        // Nivel con el que se jugó la sesión; las sesiones viejas (sin nivel)
        // usan el nivel actual del usuario.
        KnowledgeLevel level = Objects.requireNonNullElse(
            session.getKnowledgeLevel(),
            session.getUser().getKnowledgeLevel()
        );
        String feedback = Objects.requireNonNullElse(ollamaFeedbackService.generateFeedback(session, level), "");
        return Map.of("feedback", feedback);
    }
}
