package com.toastedvr.toastedvr.backend.dto;

import com.toastedvr.toastedvr.backend.config.OneTimeCodeProperties;

// Límites del código enviados al cliente para que la interfaz muestre la
// cuenta regresiva, los intentos y el bloqueo sin valores fijos.
public record OneTimeCodePolicyResponse(
    int expiresInSeconds,
    int maxAttempts,
    int maxResends,
    int resendLockMinutes
) {

    public static OneTimeCodePolicyResponse from(OneTimeCodeProperties properties) {
        return new OneTimeCodePolicyResponse(
            properties.getExpirationMinutes() * 60,
            properties.getMaxAttempts(),
            properties.getMaxResends(),
            properties.getResendLockMinutes()
        );
    }
}
