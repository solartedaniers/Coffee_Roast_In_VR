import { DTR_IDEAL_MIN_RATIO, DTR_IDEAL_MAX_RATIO, DTR_PENALTY_PER_RATIO_POINT } from './RoastConstants';

// ================================================================
// DevelopmentRatioEvaluator
// Responsabilidad única: calcular el Ratio de Desarrollo (DTR —
// Development Time Ratio, el porcentaje del tueste transcurrido
// después del first crack) y penalizar cuánto se aleja del rango
// estándar de la industria (15%-25%). Dentro del rango, sin
// penalización; fuera de él, proporcional a la distancia.
// ================================================================
export default class DevelopmentRatioEvaluator {
  static computeRatio(developmentSeconds, totalRoastSeconds) {
    if (totalRoastSeconds <= 0) return 0;
    return developmentSeconds / totalRoastSeconds;
  }

  static computePenalty(ratio) {
    const distanceBelow = Math.max(0, DTR_IDEAL_MIN_RATIO - ratio);
    const distanceAbove = Math.max(0, ratio - DTR_IDEAL_MAX_RATIO);
    return (distanceBelow + distanceAbove) * DTR_PENALTY_PER_RATIO_POINT;
  }
}
