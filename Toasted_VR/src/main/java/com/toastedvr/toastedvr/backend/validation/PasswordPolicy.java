package com.toastedvr.toastedvr.backend.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Única fuente de la regla de contraseña: la usan el registro, el cambio de
// contraseña del perfil y la recuperación.
@Component
public class PasswordPolicy {

    private final int minLength;

    public PasswordPolicy(@Value("${app.password-policy.min-length:8}") int minLength) {
        this.minLength = minLength;
    }

    public boolean isSatisfiedBy(String password) {
        return password != null
            && password.length() >= minLength
            && password.chars().anyMatch(Character::isUpperCase)
            && password.chars().anyMatch(Character::isLowerCase)
            && password.chars().anyMatch(Character::isDigit);
    }

    public int getMinLength() {
        return minLength;
    }
}
