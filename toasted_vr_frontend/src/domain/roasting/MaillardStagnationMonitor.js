import { MAILLARD_TEMP_START_C, MAILLARD_TEMP_END_C, MAILLARD_STAGNATION_LIMIT_SEC } from './RoastConstants';

// ================================================================
// MaillardStagnationMonitor
// Responsabilidad única: contar los segundos seguidos en que el grano
// no sube dentro de la fase de Maillard y decidir cuándo queda
// "horneado". Solo cuenta antes del first crack: después, el tiempo
// lo controla el DTR.
// ================================================================
export default class MaillardStagnationMonitor {
  static computeNextSeconds(previousSeconds, currentTemp, referenceTemp, firstCrackReached) {
    if (firstCrackReached) return previousSeconds;
    const inMaillard = currentTemp >= MAILLARD_TEMP_START_C && currentTemp <= MAILLARD_TEMP_END_C;
    if (!inMaillard) return previousSeconds;
    return currentTemp <= referenceTemp ? previousSeconds + 1 : 0;
  }

  static isBaked(stagnationSeconds) {
    return stagnationSeconds >= MAILLARD_STAGNATION_LIMIT_SEC;
  }
}
