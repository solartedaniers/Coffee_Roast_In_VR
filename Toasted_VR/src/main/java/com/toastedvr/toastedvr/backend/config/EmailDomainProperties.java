package com.toastedvr.toastedvr.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Revisión del dominio del correo al registrarse. knownProviders solo sirve
// para detectar errores de escritura (gail.com -> gmail.com); no es una lista
// de dominios permitidos. Debe coincidir con validation.js del frontend.
@ConfigurationProperties(prefix = "app.email-domain")
public class EmailDomainProperties {

    private List<String> knownProviders = List.of(
        "gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "icloud.com", "hotmail.es", "outlook.es", "yahoo.es"
    );
    private int maxTypoDistance = 1;
    private boolean dnsCheckEnabled = true;
    private int dnsTimeoutMillis = 2000;

    public List<String> getKnownProviders() {
        return knownProviders;
    }

    public void setKnownProviders(List<String> knownProviders) {
        this.knownProviders = knownProviders;
    }

    public int getMaxTypoDistance() {
        return maxTypoDistance;
    }

    public void setMaxTypoDistance(int maxTypoDistance) {
        this.maxTypoDistance = maxTypoDistance;
    }

    public boolean isDnsCheckEnabled() {
        return dnsCheckEnabled;
    }

    public void setDnsCheckEnabled(boolean dnsCheckEnabled) {
        this.dnsCheckEnabled = dnsCheckEnabled;
    }

    public int getDnsTimeoutMillis() {
        return dnsTimeoutMillis;
    }

    public void setDnsTimeoutMillis(int dnsTimeoutMillis) {
        this.dnsTimeoutMillis = dnsTimeoutMillis;
    }
}
