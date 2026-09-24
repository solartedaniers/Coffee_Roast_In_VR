package com.toastedvr.toastedvr.backend.validation;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Única fuente de la regla de contraseña: la usan el registro, el cambio de
// contraseña del perfil y la recuperación.
@Component
public class PasswordPolicy {

    private final int minLength;
    private final int maxLength;

    public PasswordPolicy(
        @Value("${app.password-policy.min-length:8}") int minLength,
        @Value("${app.password-policy.max-length:72}") int maxLength
    ) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public boolean isSatisfiedBy(String password) {
        return password != null
            && password.length() >= minLength
            // BCrypt solo procesa 72 bytes y rechaza entradas más largas; se mide
            // en bytes UTF-8 para que una tilde o una ñ no pasen el límite.
            && password.getBytes(StandardCharsets.UTF_8).length <= maxLength
            && password.chars().anyMatch(Character::isUpperCase)
            && password.chars().anyMatch(Character::isLowerCase)
            && password.chars().anyMatch(Character::isDigit);
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
