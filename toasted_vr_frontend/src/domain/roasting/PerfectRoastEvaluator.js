import {
  IDEAL_FINAL_TEMP_MIN_C,
  IDEAL_FINAL_TEMP_MAX_C,
  IDEAL_TOTAL_ROAST_MIN_MINUTES,
  IDEAL_TOTAL_ROAST_MAX_MINUTES,
  PERFECT_TEMP_PENALTY_WEIGHT,
  PERFECT_TIME_PENALTY_WEIGHT,
  PERFECT_SCORE_MIN,
  PERFECT_SCORE_MAX,
} from './RoastConstants';
import DevelopmentRatioEvaluator from './DevelopmentRatioEvaluator';

const distanceOutside = (value, min, max) => Math.max(0, min - value, value - max);

// ================================================================
// PerfectRoastEvaluator
// Responsabilidad única: puntuar un tueste que no cayó en ningún
// defecto, combinando qué tan cerca quedó de la temperatura ideal,
// la franja de temperatura final ideal, la de duración ideal y el Ratio
// de Desarrollo ideal.
// ================================================================
export default class PerfectRoastEvaluator {
  // Además del puntaje, devuelve cuánto restó cada penalización por
  // separado (ya redondeadas a puntos enteros) para que la interfaz
  // pueda explicar qué bajó el puntaje, sin repetir esta matemática.
  static computeBreakdown(finalTemperature, roastingElapsedSeconds, developmentSeconds, levelRules) {
    const { penaltyMultiplier, dtrToleranceRatio } = levelRules;
    const totalMinutes = roastingElapsedSeconds / 60;
    const dtr = DevelopmentRatioEvaluator.computeRatio(developmentSeconds, roastingElapsedSeconds);

    const tempPenalty = distanceOutside(finalTemperature, IDEAL_FINAL_TEMP_MIN_C, IDEAL_FINAL_TEMP_MAX_C) * PERFECT_TEMP_PENALTY_WEIGHT * penaltyMultiplier;
    const timePenalty = distanceOutside(totalMinutes, IDEAL_TOTAL_ROAST_MIN_MINUTES, IDEAL_TOTAL_ROAST_MAX_MINUTES) * PERFECT_TIME_PENALTY_WEIGHT * penaltyMultiplier;
    const dtrPenalty = DevelopmentRatioEvaluator.computePenalty(dtr, dtrToleranceRatio) * penaltyMultiplier;

    const totalPenalty = tempPenalty + timePenalty + dtrPenalty;
    const score = Math.max(PERFECT_SCORE_MIN, Math.min(PERFECT_SCORE_MAX, Math.round(PERFECT_SCORE_MAX - totalPenalty)));

    return {
      score,
      tempPenalty: Math.round(tempPenalty),
      timePenalty: Math.round(timePenalty),
      dtrPenalty: Math.round(dtrPenalty),
    };
  }
}
