import { BURN_THRESHOLD_TEMP_C, BURN_CONSECUTIVE_LIMIT_SEC } from './RoastConstants';

// ================================================================
// BurnRiskMonitor
// Responsabilidad única: contar cuánto tiempo lleva la temperatura
// del grano por encima del umbral de quemado, sin interrupción, y
// decidir cuándo esa racha significa que el lote ya se quemó.
// ================================================================
export default class BurnRiskMonitor {
  static computeConsecutiveSecondsOverThreshold(previousConsecutiveSeconds, currentTemp) {
    return currentTemp >= BURN_THRESHOLD_TEMP_C ? previousConsecutiveSeconds + 1 : 0;
  }

  static isBurned(consecutiveSecondsOverThreshold) {
    return consecutiveSecondsOverThreshold >= BURN_CONSECUTIVE_LIMIT_SEC;
  }
}
