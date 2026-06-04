package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.SaveSessionRequest;
import com.toastedvr.toastedvr.backend.dto.SessionResultResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.RoastingSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roasting")
public class RoastingSessionController {

    private final RoastingSessionService roastingSessionService;

    public RoastingSessionController(RoastingSessionService roastingSessionService) {
        this.roastingSessionService = roastingSessionService;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResultResponse saveSession(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SaveSessionRequest request
    ) {
        return roastingSessionService.saveSession(principal.getId(), request);
    }
}
