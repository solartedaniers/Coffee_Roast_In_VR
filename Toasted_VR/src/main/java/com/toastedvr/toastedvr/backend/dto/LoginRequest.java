package com.toastedvr.toastedvr.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @JsonAlias({"identifier", "username"})
    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El correo electronico no tiene un formato valido")
    String email,

    @NotBlank(message = "La contrasena es obligatoria")
    String password
) {
}
