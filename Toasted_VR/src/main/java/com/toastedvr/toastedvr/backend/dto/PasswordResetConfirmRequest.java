package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

// Tercer paso de la recuperación. resetToken sale de verificar el código; la
// coincidencia entre newPassword y confirmPassword se valida en
// PasswordResetService para reportarla en el campo confirmPassword.
public record PasswordResetConfirmRequest(
    @NotBlank(message = "{auth.passwordReset.resetExpired}")
    String resetToken,

    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    String newPassword,

    @NotBlank(message = "{validation.password.confirmationRequired}")
    String confirmPassword
) {
}
