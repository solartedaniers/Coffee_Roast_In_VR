package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.domain.KnowledgeLevel;
import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "{validation.name.required}")
    @Size(max = 120, message = "{user.profile.nameTooLong}")
    String name,

    // La longitud se valida en UserService y solo cuando el username cambia.
    @NotBlank(message = "{validation.username.required}")
    String username,

    @Size(max = 2000000, message = "{user.profile.imageTooLarge}")
    String profileImageUrl,

    KnowledgeLevel knowledgeLevel,

    String currentPassword,

    @ValidPassword
    String newPassword
) {
}
