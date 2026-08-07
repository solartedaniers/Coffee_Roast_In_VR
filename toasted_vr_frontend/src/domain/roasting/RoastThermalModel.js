import {
  AMBIENT_TEMP_C,
  MAX_SAFE_TEMP_C,
  BURNER_MAX_HEAT_RATE_C_PER_SEC,
  HEAT_LOSS_COEFF_PER_SEC,
  FLAME_RESPONSE_LAG_PER_SEC,
  CHARGE_DIP_DURATION_SEC,
  CHARGE_DIP_PEAK_LOSS_C_PER_SEC,
  AUTO_MODE_BASE_POWER_PCT,
  AUTO_MODE_GAIN_PCT_PER_C,
  FLAME_POWER_MIN_PCT,
  FLAME_POWER_MAX_PCT,
} from './RoastConstants';

// ================================================================
// RoastThermalModel
// Responsabilidad única: la matemática de transferencia de calor
// del tambor/grano. Puro y sin estado — cada método recibe lo que
// necesita y devuelve un número derivado. Sin React, sin efectos
// secundarios.
// ================================================================
export default class RoastThermalModel {
  // La potencia de llama responde de forma gradual (pulsos de la
  // válvula de gas + masa térmica del tambor) en vez de saltar de
  // inmediato al valor que fija el operador.
  static computeEffectivePower(currentEffectivePower, setPowerPercent) {
    return currentEffectivePower + (setPowerPercent - currentEffectivePower) * FLAME_RESPONSE_LAG_PER_SEC;
  }

  static computeHeatRatePerSecond(effectivePowerPercent, currentTemp) {
    const heatGain = (effectivePowerPercent / 100) * BURNER_MAX_HEAT_RATE_C_PER_SEC;
    const heatLoss = HEAT_LOSS_COEFF_PER_SEC * (currentTemp - AMBIENT_TEMP_C);
    return heatGain - heatLoss;
  }

  // Calor extra que se le resta al tambor justo después de cargar
  // granos fríos, decayendo linealmente a cero en CHARGE_DIP_DURATION_SEC.
  static computeChargeDipLossPerSecond(secondsSinceCharge) {
    const progress = Math.min(1, secondsSinceCharge / CHARGE_DIP_DURATION_SEC);
    return CHARGE_DIP_PEAK_LOSS_C_PER_SEC * (1 - progress);
  }

  static computeNextTemperature(currentTemp, ratePerSecond) {
    const next = currentTemp + ratePerSecond;
    return parseFloat(Math.min(MAX_SAFE_TEMP_C, Math.max(AMBIENT_TEMP_C, next)).toFixed(2));
  }

  // Controlador proporcional usado solo en modo AUTO: mientras más
  // lejos está la temperatura del café de Temp Ctrl, más llama pide.
  static computeAutoFlamePowerPercent(currentTemp, targetTemp) {
    const error = targetTemp - currentTemp;
    const power = AUTO_MODE_BASE_POWER_PCT + AUTO_MODE_GAIN_PCT_PER_C * error;
    return Math.min(FLAME_POWER_MAX_PCT, Math.max(FLAME_POWER_MIN_PCT, Math.round(power)));
  }
}
