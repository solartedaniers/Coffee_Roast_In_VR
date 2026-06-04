package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.AuthenticatedUserResponse;
import com.toastedvr.toastedvr.backend.dto.UpdateKnowledgeLevelRequest;
import com.toastedvr.toastedvr.backend.security.UserPrincipal;
import com.toastedvr.toastedvr.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me/knowledge-level")
    public AuthenticatedUserResponse updateKnowledgeLevel(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody UpdateKnowledgeLevelRequest request
    ) {
        return userService.updateKnowledgeLevel(principal.getId(), request.knowledgeLevel());
    }
}
