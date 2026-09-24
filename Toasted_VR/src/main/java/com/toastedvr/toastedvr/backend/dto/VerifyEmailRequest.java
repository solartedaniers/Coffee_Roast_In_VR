package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
    @NotBlank(message = "{validation.email.required}")
    @ValidEmail
    String email,

    @NotBlank(message = "{validation.code.required}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.code.format}")
    String code
) {
}
