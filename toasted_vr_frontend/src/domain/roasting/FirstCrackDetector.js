import {
  FIRST_CRACK_TEMP_MIN_C,
  FIRST_CRACK_TEMP_MAX_C,
  BEAN_DENSITY_REFERENCE,
  CRACK_DENSITY_SHIFT_C_PER_DENSITY_UNIT,
} from './RoastConstants';

// ================================================================
// FirstCrackDetector
// Responsabilidad única: decidir a qué temperatura del grano se
// dispara el first crack en esta sesión, y detectar cuándo ocurre.
//
// El first crack no es un número fijo — en granos reales ocurre en
// algún punto entre ~189-198°C, desplazado por la densidad del grano
// (más denso, tarda más). La temperatura que lo dispara se sortea una
// sola vez por sesión dentro de ese rango.
// ================================================================
export default class FirstCrackDetector {
  static pickThresholdTemperature(density) {
    const span = FIRST_CRACK_TEMP_MAX_C - FIRST_CRACK_TEMP_MIN_C;
    const base = FIRST_CRACK_TEMP_MIN_C + Math.random() * span;
    const densityShift = (density - BEAN_DENSITY_REFERENCE) * CRACK_DENSITY_SHIFT_C_PER_DENSITY_UNIT;
    return parseFloat((base + densityShift).toFixed(1));
  }

  static hasReachedFirstCrack(currentTemp, thresholdTemp) {
    return currentTemp >= thresholdTemp;
  }
}
