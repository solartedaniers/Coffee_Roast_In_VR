import { FIRST_CRACK_TEMP_MIN_C, FIRST_CRACK_TEMP_MAX_C } from './RoastConstants';

// ================================================================
// FirstCrackDetector
// Responsabilidad única: decidir a qué temperatura del grano se
// dispara el first crack en esta sesión, y detectar cuándo ocurre.
//
// El first crack no es un número fijo — en granos reales ocurre en
// algún punto entre ~189-198°C. Un incremento de temperatura agresivo
// llega ahí en menos minutos que uno suave, pero la temperatura que lo
// dispara se sortea una sola vez por sesión dentro de ese rango.
// ================================================================
export default class FirstCrackDetector {
  static pickThresholdTemperature() {
    const span = FIRST_CRACK_TEMP_MAX_C - FIRST_CRACK_TEMP_MIN_C;
    return parseFloat((FIRST_CRACK_TEMP_MIN_C + Math.random() * span).toFixed(1));
  }

  static hasReachedFirstCrack(currentTemp, thresholdTemp) {
    return currentTemp >= thresholdTemp;
  }
}
