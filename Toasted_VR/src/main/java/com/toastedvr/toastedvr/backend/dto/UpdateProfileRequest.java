package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres.")
    String name,

    @NotBlank(message = "El nombre de usuario es obligatorio.")
    @Size(min = 3, max = 40, message = "El usuario debe tener entre 3 y 40 caracteres.")
    String username,

    @Size(max = 2000000, message = "La imagen de perfil es demasiado grande.")
    String profileImageUrl,

    KnowledgeLevel knowledgeLevel,

    String currentPassword,

    @Size(min = 8, max = 120, message = "La nueva contraseña debe tener entre 8 y 120 caracteres.")
    String newPassword
) {
}
