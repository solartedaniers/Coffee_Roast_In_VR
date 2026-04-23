package com.toastedvr.toastedvr.backend.controller;

import com.toastedvr.toastedvr.backend.dto.LoginRequest;
import com.toastedvr.toastedvr.backend.dto.LoginResponse;
import com.toastedvr.toastedvr.backend.dto.LogoutResponse;
import com.toastedvr.toastedvr.backend.dto.RegisterUserRequest;
import com.toastedvr.toastedvr.backend.dto.RegisterUserResponse;
import com.toastedvr.toastedvr.backend.dto.UserResponse;
import com.toastedvr.toastedvr.backend.dto.VerifyEmailRequest;
import com.toastedvr.toastedvr.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterUserResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/verify-email")
    public UserResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        String token = authorizationHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        return authService.logout(token);
    }
}
