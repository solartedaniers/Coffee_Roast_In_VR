package com.toastedvr.toastedvr.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @JsonAlias({"identifier", "username"})
    @NotBlank(message = "{validation.email.required}")
    @ValidEmail
    String email,

    @NotBlank(message = "{validation.password.required}")
    String password
) {
}
