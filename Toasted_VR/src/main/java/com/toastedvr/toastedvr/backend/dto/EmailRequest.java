package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

public record EmailRequest(
    @NotBlank(message = "{validation.email.required}")
    @ValidEmail
    String email
) {
}
