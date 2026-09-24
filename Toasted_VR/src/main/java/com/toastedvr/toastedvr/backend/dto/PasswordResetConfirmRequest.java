package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidEmail;
import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// La coincidencia entre newPassword y confirmPassword se valida en
// PasswordResetService para reportarla en el campo confirmPassword.
public record PasswordResetConfirmRequest(
    @NotBlank(message = "{validation.email.required}")
    @ValidEmail
    String email,

    @NotBlank(message = "{validation.code.required}")
    @Pattern(regexp = "^\\d{6}$", message = "{validation.code.format}")
    String code,

    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    String newPassword,

    @NotBlank(message = "{validation.password.confirmationRequired}")
    String confirmPassword
) {
}
