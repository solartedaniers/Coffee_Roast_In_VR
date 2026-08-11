import { RESULTS, DEFECT_SCORE_MIN, DEFECT_SCORE_MAX } from './RoastConstants';
import KnowledgeLevelRules from './KnowledgeLevelRules';
import RawRoastEvaluator from './RawRoastEvaluator';
import BurnedRoastEvaluator from './BurnedRoastEvaluator';
import MaillardStagnationEvaluator from './MaillardStagnationEvaluator';
import PerfectRoastEvaluator from './PerfectRoastEvaluator';

function clampDefectScore(baseScore, adjustment) {
  return Math.max(DEFECT_SCORE_MIN, Math.min(DEFECT_SCORE_MAX, Math.round(baseScore + adjustment)));
}

// ================================================================
// RoastQualityEvaluator
// Responsabilidad única: decidir cuál de los cuatro resultados
// posibles aplica a un tueste terminado — Crudo, Quemado, Horneado o
// Perfecto, en ese orden de prioridad — y delegar el cálculo del
// puntaje al evaluador específico de ese resultado.
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
      maillardStagnationSeconds,
      maillardStagnationFlag,
    } = finishedSim;

    if (RawRoastEvaluator.isRaw(firstCrackReached, finalTemperature)) {
      const baseScore = RawRoastEvaluator.computeBaseScore(finalTemperature);
      return { score: clampDefectScore(baseScore, rules.defectScoreAdjustment), result: RESULTS.RAW };
    }

    if (BurnedRoastEvaluator.isBurned(burnedFlag, finalTemperature)) {
      const baseScore = BurnedRoastEvaluator.computeBaseScore(maxConsecutiveBurnSeconds);
      return { score: clampDefectScore(baseScore, rules.defectScoreAdjustment), result: RESULTS.BURNED };
    }

    if (maillardStagnationFlag) {
      const baseScore = MaillardStagnationEvaluator.computeBaseScore(maillardStagnationSeconds);
      return { score: clampDefectScore(baseScore, rules.defectScoreAdjustment), result: RESULTS.BAKED };
    }

    const developmentSeconds = roastingElapsedSeconds - (firstCrackTimeSeconds || 0);
    const score = PerfectRoastEvaluator.computeScore(
      finalTemperature,
      roastingElapsedSeconds,
      developmentSeconds,
      rules.penaltyMultiplier
    );
    return { score, result: RESULTS.PERFECT };
  }
}
