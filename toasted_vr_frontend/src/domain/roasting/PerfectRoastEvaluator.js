import {
  IDEAL_FINAL_TEMP_C,
  IDEAL_TOTAL_ROAST_MINUTES,
  PERFECT_TEMP_PENALTY_WEIGHT,
  PERFECT_TIME_PENALTY_WEIGHT,
  PERFECT_SCORE_MIN,
  PERFECT_SCORE_MAX,
} from './RoastConstants';
import DevelopmentRatioEvaluator from './DevelopmentRatioEvaluator';

// ================================================================
// PerfectRoastEvaluator
// Responsabilidad única: puntuar un tueste que no cayó en ningún
// defecto, combinando qué tan cerca quedó de la temperatura ideal,
// el tiempo total ideal y el Ratio de Desarrollo ideal.
// ================================================================
export default class PerfectRoastEvaluator {
  static computeScore(finalTemperature, roastingElapsedSeconds, developmentSeconds, penaltyMultiplier) {
    const totalMinutes = roastingElapsedSeconds / 60;
    const dtr = DevelopmentRatioEvaluator.computeRatio(developmentSeconds, roastingElapsedSeconds);

    const tempPenalty = Math.abs(finalTemperature - IDEAL_FINAL_TEMP_C) * PERFECT_TEMP_PENALTY_WEIGHT;
    const timePenalty = Math.abs(totalMinutes - IDEAL_TOTAL_ROAST_MINUTES) * PERFECT_TIME_PENALTY_WEIGHT;
    const dtrPenalty = DevelopmentRatioEvaluator.computePenalty(dtr);

    const totalPenalty = (tempPenalty + timePenalty + dtrPenalty) * penaltyMultiplier;
    return Math.max(PERFECT_SCORE_MIN, Math.min(PERFECT_SCORE_MAX, Math.round(PERFECT_SCORE_MAX - totalPenalty)));
  }
}
