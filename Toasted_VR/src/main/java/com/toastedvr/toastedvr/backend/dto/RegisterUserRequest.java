package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import com.toastedvr.toastedvr.backend.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserRequest(
    @NotBlank(message = "El nombre es obligatorio")
    String name,

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "Debes ingresar un correo electronico valido")
    String email,

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @ValidUsername
    String username,

    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    String password
) {
}
