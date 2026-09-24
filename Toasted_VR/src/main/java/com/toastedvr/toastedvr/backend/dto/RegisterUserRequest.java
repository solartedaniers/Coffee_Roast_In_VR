package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import com.toastedvr.toastedvr.backend.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
    @NotBlank(message = "{validation.name.required}")
    String name,

    @NotBlank(message = "{validation.email.required}")
    @ValidEmail
    String email,

    @NotBlank(message = "{validation.username.required}")
    @ValidUsername
    String username,

    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    String password
) {
}
