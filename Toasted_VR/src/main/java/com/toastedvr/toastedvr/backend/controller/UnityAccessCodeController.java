package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.CurrentPasswordRequest;
import com.toastedvr.toastedvr.backend.dto.UnityAccessCodeResponse;
import com.toastedvr.toastedvr.backend.dto.UnityAccessCodeStatusResponse;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.UnityAccessCodeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/unity-access-code")
public class UnityAccessCodeController {

    private final UnityAccessCodeService unityAccessCodeService;

    public UnityAccessCodeController(UnityAccessCodeService unityAccessCodeService) {
        this.unityAccessCodeService = unityAccessCodeService;
    }

    @GetMapping
    public UnityAccessCodeStatusResponse getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        return unityAccessCodeService.getStatus(principal.getId());
    }

    @PostMapping
    public UnityAccessCodeResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CurrentPasswordRequest request
    ) {
        return unityAccessCodeService.create(principal.getId(), request.currentPassword());
    }

    @PostMapping("/reveal")
    public UnityAccessCodeResponse reveal(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CurrentPasswordRequest request
    ) {
        return unityAccessCodeService.reveal(principal.getId(), request.currentPassword());
    }

    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void email(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CurrentPasswordRequest request
    ) {
        unityAccessCodeService.email(principal.getId(), request.currentPassword());
    }

    @PostMapping("/regenerate")
    public UnityAccessCodeResponse regenerate(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CurrentPasswordRequest request
    ) {
        return unityAccessCodeService.regenerate(principal.getId(), request.currentPassword());
    }
}
