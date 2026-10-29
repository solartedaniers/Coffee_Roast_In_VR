package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Conexión con el servicio IA (carpeta IA/), que corre fuera de Docker.
@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {

    private String baseUrl = "http://localhost:8082";
    private int connectTimeoutMillis = 5_000;
    private int feedbackReadTimeoutMillis = 130_000;
    private int ingestionReadTimeoutMillis = 1_800_000;
    // Mayor que VOICE_TOTAL_TIMEOUT_MILLIS del servicio IA (25 s), que corta antes.
    private int voiceReadTimeoutMillis = 30_000;
    // Mayor que ALERTS_PACKAGE_TIMEOUT_MILLIS del servicio IA (20 s), que corta antes.
    private int alertsReadTimeoutMillis = 30_000;

    public int getAlertsReadTimeoutMillis() {
        return alertsReadTimeoutMillis;
    }

    public void setAlertsReadTimeoutMillis(int alertsReadTimeoutMillis) {
        this.alertsReadTimeoutMillis = alertsReadTimeoutMillis;
    }

    public int getVoiceReadTimeoutMillis() {
        return voiceReadTimeoutMillis;
    }

    public void setVoiceReadTimeoutMillis(int voiceReadTimeoutMillis) {
        this.voiceReadTimeoutMillis = voiceReadTimeoutMillis;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getFeedbackReadTimeoutMillis() {
        return feedbackReadTimeoutMillis;
    }

    public void setFeedbackReadTimeoutMillis(int feedbackReadTimeoutMillis) {
        this.feedbackReadTimeoutMillis = feedbackReadTimeoutMillis;
    }

    public int getIngestionReadTimeoutMillis() {
        return ingestionReadTimeoutMillis;
    }

    public void setIngestionReadTimeoutMillis(int ingestionReadTimeoutMillis) {
        this.ingestionReadTimeoutMillis = ingestionReadTimeoutMillis;
    }
}
