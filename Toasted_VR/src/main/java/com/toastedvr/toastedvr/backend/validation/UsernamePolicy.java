package com.toastedvr.toastedvr.backend.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Longitud permitida del nombre de usuario, compartida por el registro y el perfil.
@Component
public class UsernamePolicy {

    private final int minLength;
    private final int maxLength;

    public UsernamePolicy(
        @Value("${app.username-policy.min-length:4}") int minLength,
        @Value("${app.username-policy.max-length:20}") int maxLength
    ) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public boolean isSatisfiedBy(String username) {
        return username != null && username.length() >= minLength && username.length() <= maxLength;
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }
}
