import {
  BURN_ABSOLUTE_CEILING_TEMP_C,
  ROR_SCORCH_THRESHOLD_C_PER_MIN,
  MOISTURE_SCORCH_THRESHOLD_PCT,
} from './RoastConstants';

// ================================================================
// BurnRiskMonitor
// Responsabilidad única: decidir cuándo el lote se quemó, por tres
// vías independientes: (1) pasar del techo del tueste medio, (2)
// llegar al segundo crack, (3) un incremento de temperatura demasiado
// agresivo cuando ya casi no queda humedad para amortiguarlo. Estar
// sobre 200°C después del first crack no quema por sí solo: eso lo
// controla el DTR.
// ================================================================
export default class BurnRiskMonitor {
  // Segundos seguidos sobre el techo; solo gradúan el puntaje del quemado.
  static computeConsecutiveSecondsOverThreshold(previousConsecutiveSeconds, currentTemp) {
    return currentTemp > BURN_ABSOLUTE_CEILING_TEMP_C ? previousConsecutiveSeconds + 1 : 0;
  }

  static isBurned(currentTemp, secondCrackReached) {
    return currentTemp > BURN_ABSOLUTE_CEILING_TEMP_C || secondCrackReached;
  }

  static isScorchedByRateAndMoisture(rateOfRisePerMinute, moisturePct) {
    return rateOfRisePerMinute >= ROR_SCORCH_THRESHOLD_C_PER_MIN && moisturePct <= MOISTURE_SCORCH_THRESHOLD_PCT;
  }
}
