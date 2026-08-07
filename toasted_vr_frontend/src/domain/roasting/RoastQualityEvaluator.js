import {
  AMBIENT_TEMP_C,
  RAW_TEMP_CEILING_C,
  BURN_THRESHOLD_TEMP_C,
  BURN_CONSECUTIVE_LIMIT_SEC,
  IDEAL_FINAL_TEMP_C,
  IDEAL_TOTAL_ROAST_MINUTES,
  IDEAL_DEVELOPMENT_MINUTES,
  RESULTS,
} from './RoastConstants';
import KnowledgeLevelRules from './KnowledgeLevelRules';

function clampDefectScore(score, adjustment) {
  return Math.max(5, Math.min(50, Math.round(score + adjustment)));
}

// ================================================================
// RoastQualityEvaluator
// Responsabilidad única: convertir las estadísticas de un tueste
// terminado en una etiqueta de resultado (PERFECT/RAW/BURNED/BAKED)
// y una puntuación de 0 a 100.
// ================================================================
export default class RoastQualityEvaluator {
  static evaluate(finishedSim, knowledgeLevel) {
    const rules = KnowledgeLevelRules.forLevel(knowledgeLevel);
    const {
      finalTemperature,
      maxConsecutiveBurnSeconds,
      burnedFlag,
      firstCrackReached,
      firstCrackTimeSeconds,
      roastingElapsedSeconds,
      maillardStagnationFlag,
    } = finishedSim;

    if (!firstCrackReached || finalTemperature < RAW_TEMP_CEILING_C) {
      const rawRatio = Math.max(0, (finalTemperature - AMBIENT_TEMP_C) / (RAW_TEMP_CEILING_C - AMBIENT_TEMP_C));
      return { score: clampDefectScore(Math.round(rawRatio * 38), rules.defectScoreAdjustment), result: RESULTS.RAW };
    }

    if (burnedFlag || finalTemperature > BURN_THRESHOLD_TEMP_C + 5) {
      const overrun = Math.max(0, maxConsecutiveBurnSeconds - BURN_CONSECUTIVE_LIMIT_SEC) * 0.4;
      return {
        score: clampDefectScore(Math.max(5, Math.round(18 - overrun)), rules.defectScoreAdjustment),
        result: RESULTS.BURNED,
      };
    }

    if (maillardStagnationFlag) {
      return { score: clampDefectScore(45, rules.defectScoreAdjustment), result: RESULTS.BAKED };
    }

    const developmentSeconds = roastingElapsedSeconds - (firstCrackTimeSeconds || 0);
    const totalMinutes = roastingElapsedSeconds / 60;
    const developmentMinutes = developmentSeconds / 60;

    const tempPenalty = Math.abs(finalTemperature - IDEAL_FINAL_TEMP_C) * 2.5 * rules.penaltyMultiplier;
    const timePenalty = Math.abs(totalMinutes - IDEAL_TOTAL_ROAST_MINUTES) * 4 * rules.penaltyMultiplier;
    const devPenalty = Math.abs(developmentMinutes - IDEAL_DEVELOPMENT_MINUTES) * 8 * rules.penaltyMultiplier;

    return {
      score: Math.max(51, Math.min(100, Math.round(100 - tempPenalty - timePenalty - devPenalty))),
      result: RESULTS.PERFECT,
    };
  }
}
