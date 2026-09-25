package com.toastedvr.toastedvr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Límites con los que el servidor revisa una sesión de tueste antes de
// guardarla (RF015). Copian las constantes del simulador (RoastConstants.js);
// un test del frontend avisa si se separan.
@ConfigurationProperties(prefix = "app.roast-validation")
public class RoastValidationProperties {

    // La carga es la temperatura real del aire al cargar, no la del slider:
    // solo se exige que sea físicamente posible en el modelo térmico.
    private double chargeTemperatureMin = 0;
    private double chargeTemperatureMax = 750;
    private double targetTemperatureMin = 150;
    private double targetTemperatureMax = 230;
    private double finalTemperatureMax = 250;
    private double rawTemperatureCeiling = 186;
    private double burnedTemperatureCeiling = 217;
    private int defectScoreMin = 5;
    private int defectScoreMax = 50;
    private int perfectScoreMin = 0;
    private int perfectScoreMax = 100;

    public double getChargeTemperatureMin() {
        return chargeTemperatureMin;
    }

    public void setChargeTemperatureMin(double chargeTemperatureMin) {
        this.chargeTemperatureMin = chargeTemperatureMin;
    }

    public double getChargeTemperatureMax() {
        return chargeTemperatureMax;
    }

    public void setChargeTemperatureMax(double chargeTemperatureMax) {
        this.chargeTemperatureMax = chargeTemperatureMax;
    }

    public double getTargetTemperatureMin() {
        return targetTemperatureMin;
    }

    public void setTargetTemperatureMin(double targetTemperatureMin) {
        this.targetTemperatureMin = targetTemperatureMin;
    }

    public double getTargetTemperatureMax() {
        return targetTemperatureMax;
    }

    public void setTargetTemperatureMax(double targetTemperatureMax) {
        this.targetTemperatureMax = targetTemperatureMax;
    }

    public double getFinalTemperatureMax() {
        return finalTemperatureMax;
    }

    public void setFinalTemperatureMax(double finalTemperatureMax) {
        this.finalTemperatureMax = finalTemperatureMax;
    }

    public double getRawTemperatureCeiling() {
        return rawTemperatureCeiling;
    }

    public void setRawTemperatureCeiling(double rawTemperatureCeiling) {
        this.rawTemperatureCeiling = rawTemperatureCeiling;
    }

    public double getBurnedTemperatureCeiling() {
        return burnedTemperatureCeiling;
    }

    public void setBurnedTemperatureCeiling(double burnedTemperatureCeiling) {
        this.burnedTemperatureCeiling = burnedTemperatureCeiling;
    }

    public int getDefectScoreMin() {
        return defectScoreMin;
    }

    public void setDefectScoreMin(int defectScoreMin) {
        this.defectScoreMin = defectScoreMin;
    }

    public int getDefectScoreMax() {
        return defectScoreMax;
    }

    public void setDefectScoreMax(int defectScoreMax) {
        this.defectScoreMax = defectScoreMax;
    }

    public int getPerfectScoreMin() {
        return perfectScoreMin;
    }

    public void setPerfectScoreMin(int perfectScoreMin) {
        this.perfectScoreMin = perfectScoreMin;
    }

    public int getPerfectScoreMax() {
        return perfectScoreMax;
    }

    public void setPerfectScoreMax(int perfectScoreMax) {
        this.perfectScoreMax = perfectScoreMax;
    }
}
