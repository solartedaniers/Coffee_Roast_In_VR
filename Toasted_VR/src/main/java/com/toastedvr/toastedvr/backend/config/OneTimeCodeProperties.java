package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Límites comunes a los códigos de verificación y de recuperación de contraseña.
@ConfigurationProperties(prefix = "app.one-time-code")
public class OneTimeCodeProperties {

    private int expirationMinutes = 1;
    private int maxAttempts = 5;
    private int maxResends = 3;
    private int resendLockMinutes = 5;

    public int getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(int expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxResends() {
        return maxResends;
    }

    public void setMaxResends(int maxResends) {
        this.maxResends = maxResends;
    }

    public int getResendLockMinutes() {
        return resendLockMinutes;
    }

    public void setResendLockMinutes(int resendLockMinutes) {
        this.resendLockMinutes = resendLockMinutes;
    }
}
