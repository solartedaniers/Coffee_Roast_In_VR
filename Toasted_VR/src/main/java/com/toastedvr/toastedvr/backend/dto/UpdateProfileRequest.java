package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
    String name,

    // La longitud se valida en UserService y solo cuando el username cambia.
    @NotBlank(message = "El nombre de usuario es obligatorio.")
    String username,

    @Size(max = 2000000, message = "La imagen de perfil es demasiado grande.")
    String profileImageUrl,

    KnowledgeLevel knowledgeLevel,

    String currentPassword,

    @ValidPassword
    String newPassword
) {
}
