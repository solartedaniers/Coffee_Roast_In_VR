package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Conexión con el simulador de tueste en Node (carpeta roast-simulator/), que corre fuera de Docker.
@ConfigurationProperties(prefix = "app.roast-simulator")
public class RoastSimulatorProperties {

    private String baseUrl = "http://127.0.0.1:8083";
    private int connectTimeoutMillis = 2_000;
    private int readTimeoutMillis = 5_000;

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

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
