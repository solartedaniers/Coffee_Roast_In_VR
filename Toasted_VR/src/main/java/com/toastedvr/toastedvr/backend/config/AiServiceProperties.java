package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Conexión con el servicio IA (carpeta IA/), que corre fuera de Docker.
@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {

    private String baseUrl = "http://localhost:8082";
    private int connectTimeoutMillis = 5_000;
    private int feedbackReadTimeoutMillis = 130_000;
    private int ingestionReadTimeoutMillis = 1_800_000;

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
