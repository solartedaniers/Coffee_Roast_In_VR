package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Tamaño máximo y tolerancias de la traza que acompaña una sesión. Deben coincidir con
// RoastTraceRecorder.js del frontend; un test del frontend avisa si se separan.
@ConfigurationProperties(prefix = "app.roast-trace")
public class RoastTraceProperties {

    private int supportedVersion = 1;
    private int maxSamples = 400;
    private int maxGasChanges = 120;
    private int maxEvents = 10;
    private int maxAlerts = 60;
    private int maxAlertIdLength = 40;
    // Diferencia aceptada entre los segundos de la traza y los de la sesión (redondeos del cliente).
    private int timeToleranceSeconds = 5;
    private double temperatureTolerance = 1.0;

    public int getSupportedVersion() {
        return supportedVersion;
    }

    public void setSupportedVersion(int supportedVersion) {
        this.supportedVersion = supportedVersion;
    }

    public int getMaxSamples() {
        return maxSamples;
    }

    public void setMaxSamples(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    public int getMaxGasChanges() {
        return maxGasChanges;
    }

    public void setMaxGasChanges(int maxGasChanges) {
        this.maxGasChanges = maxGasChanges;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(int maxEvents) {
        this.maxEvents = maxEvents;
    }

    public int getMaxAlerts() {
        return maxAlerts;
    }

    public void setMaxAlerts(int maxAlerts) {
        this.maxAlerts = maxAlerts;
    }

    public int getMaxAlertIdLength() {
        return maxAlertIdLength;
    }

    public void setMaxAlertIdLength(int maxAlertIdLength) {
        this.maxAlertIdLength = maxAlertIdLength;
    }

    public int getTimeToleranceSeconds() {
        return timeToleranceSeconds;
    }

    public void setTimeToleranceSeconds(int timeToleranceSeconds) {
        this.timeToleranceSeconds = timeToleranceSeconds;
    }

    public double getTemperatureTolerance() {
        return temperatureTolerance;
    }

    public void setTemperatureTolerance(double temperatureTolerance) {
        this.temperatureTolerance = temperatureTolerance;
    }
}
