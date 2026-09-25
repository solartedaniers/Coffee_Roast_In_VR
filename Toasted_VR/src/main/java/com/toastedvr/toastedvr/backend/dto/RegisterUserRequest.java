package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import com.toastedvr.toastedvr.backend.validation.ValidUsername;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterUserRequest(
    // Letras de cualquier idioma (incluye tildes y ñ) y espacios entre palabras.
    @NotBlank(message = "{validation.name.required}")
    @Pattern(regexp = "^[\\p{L}\\p{M} ]*$", message = "{validation.name.lettersOnly}")
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
