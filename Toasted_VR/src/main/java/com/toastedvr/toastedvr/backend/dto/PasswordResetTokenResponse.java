package com.toastedvr.toastedvr.backend.dto;

// Código correcto: resetToken autoriza el cambio de contraseña durante expiresInSeconds.
public record PasswordResetTokenResponse(String message, String resetToken, long expiresInSeconds) {
}
