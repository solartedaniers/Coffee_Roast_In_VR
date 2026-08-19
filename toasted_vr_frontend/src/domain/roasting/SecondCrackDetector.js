import {
  SECOND_CRACK_TEMP_MIN_C,
  SECOND_CRACK_TEMP_MAX_C,
  BEAN_DENSITY_REFERENCE,
  CRACK_DENSITY_SHIFT_C_PER_DENSITY_UNIT,
} from './RoastConstants';

// ================================================================
// SecondCrackDetector
// Responsabilidad única: mismo patrón que FirstCrackDetector, para el
// segundo crack (224-228°C). Un grano más denso que el de referencia
// aguanta un poco más antes de que reviente por segunda vez.
// ================================================================
export default class SecondCrackDetector {
  static pickThresholdTemperature(density) {
    const span = SECOND_CRACK_TEMP_MAX_C - SECOND_CRACK_TEMP_MIN_C;
    const base = SECOND_CRACK_TEMP_MIN_C + Math.random() * span;
    const densityShift = (density - BEAN_DENSITY_REFERENCE) * CRACK_DENSITY_SHIFT_C_PER_DENSITY_UNIT;
    return parseFloat((base + densityShift).toFixed(1));
  }

  static hasReachedSecondCrack(currentTemp, thresholdTemp) {
    return currentTemp >= thresholdTemp;
  }
}
