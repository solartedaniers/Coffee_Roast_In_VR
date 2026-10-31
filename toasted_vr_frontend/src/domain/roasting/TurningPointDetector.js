import { TURNING_POINT_MIN_RISE_C } from './RoastConstants';

// ================================================================
// TurningPointDetector
// Responsabilidad única: decidir cuándo la sonda tocó fondo después de
// la carga y empezó a subir (punto de retorno). La sigue igual que las
// alertas y la rúbrica de la IA: la mínima desde la carga y una subida
// de al menos TURNING_POINT_MIN_RISE_C sobre ella.
// ================================================================
export default class TurningPointDetector {
  static hasReachedTurningPoint(currentProbeTemp, lowestProbeSinceCharge) {
    return currentProbeTemp - lowestProbeSinceCharge >= TURNING_POINT_MIN_RISE_C;
  }
}
