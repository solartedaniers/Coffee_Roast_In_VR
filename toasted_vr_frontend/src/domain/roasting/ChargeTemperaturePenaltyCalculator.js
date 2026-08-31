import { CHARGE_TEMP_IDEAL_MIN_C, CHARGE_TEMP_IDEAL_MAX_C, CHARGE_TEMP_PENALTY_WEIGHT } from './RoastConstants';

// ================================================================
// ChargeTemperaturePenaltyCalculator
// Responsabilidad única: penalizar el puntaje cuando la temperatura
// de carga elegida por el usuario queda fuera del rango recomendado
// (180-200°C). Dentro del rango, sin penalización; fuera de él,
// proporcional a la distancia — mismo patrón que
// DevelopmentRatioEvaluator.computePenalty.
// ================================================================
export default class ChargeTemperaturePenaltyCalculator {
  static computePenalty(chargeTemperature) {
    const distanceBelow = Math.max(0, CHARGE_TEMP_IDEAL_MIN_C - chargeTemperature);
    const distanceAbove = Math.max(0, chargeTemperature - CHARGE_TEMP_IDEAL_MAX_C);
    return (distanceBelow + distanceAbove) * CHARGE_TEMP_PENALTY_WEIGHT;
  }
}
