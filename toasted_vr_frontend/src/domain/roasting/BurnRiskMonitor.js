import {
  BURN_THRESHOLD_TEMP_C,
  BURN_CONSECUTIVE_LIMIT_SEC,
  ROR_SCORCH_THRESHOLD_C_PER_MIN,
  MOISTURE_SCORCH_THRESHOLD_PCT,
} from './RoastConstants';

// ================================================================
// BurnRiskMonitor
// Responsabilidad única: decidir cuándo el lote se quemó, por dos
// vías independientes: (1) tiempo consecutivo por encima del umbral,
// (2) un incremento de temperatura demasiado agresivo cuando ya casi
// no queda humedad para amortiguarlo.
// ================================================================
export default class BurnRiskMonitor {
  static computeConsecutiveSecondsOverThreshold(previousConsecutiveSeconds, currentTemp) {
    return currentTemp >= BURN_THRESHOLD_TEMP_C ? previousConsecutiveSeconds + 1 : 0;
  }

  static isBurned(consecutiveSecondsOverThreshold) {
    return consecutiveSecondsOverThreshold >= BURN_CONSECUTIVE_LIMIT_SEC;
  }

  static isScorchedByRateAndMoisture(rateOfRisePerMinute, moisturePct) {
    return rateOfRisePerMinute >= ROR_SCORCH_THRESHOLD_C_PER_MIN && moisturePct <= MOISTURE_SCORCH_THRESHOLD_PCT;
  }
}
