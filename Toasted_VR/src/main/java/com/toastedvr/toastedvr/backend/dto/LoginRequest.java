package com.toastedvr.toastedvr.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @JsonAlias("username")
    @NotBlank(message = "El identificador es obligatorio")
    String identifier,

    @NotBlank(message = "La contrasena es obligatoria")
    String password
) {
}
