import { DTR_IDEAL_MIN_RATIO, DTR_IDEAL_MAX_RATIO, DTR_PENALTY_PER_RATIO_POINT } from './RoastConstants';

// ================================================================
// DevelopmentRatioEvaluator
// Responsabilidad única: calcular el Ratio de Desarrollo (DTR —
// Development Time Ratio, el porcentaje del tueste transcurrido
// después del first crack) y penalizar cuánto se aleja del rango
// ideal (18%-22%) ampliado por la tolerancia del nivel. Dentro de
// esa franja, sin penalización; fuera de ella, proporcional a la
// distancia.
// ================================================================
export default class DevelopmentRatioEvaluator {
  static computeRatio(developmentSeconds, totalRoastSeconds) {
    if (totalRoastSeconds <= 0) return 0;
    return developmentSeconds / totalRoastSeconds;
  }

  static computePenalty(ratio, toleranceRatio = 0) {
    const distanceBelow = Math.max(0, DTR_IDEAL_MIN_RATIO - toleranceRatio - ratio);
    const distanceAbove = Math.max(0, ratio - (DTR_IDEAL_MAX_RATIO + toleranceRatio));
    return (distanceBelow + distanceAbove) * DTR_PENALTY_PER_RATIO_POINT;
  }
}
