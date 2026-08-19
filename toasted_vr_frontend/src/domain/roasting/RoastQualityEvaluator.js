import {
  RESULTS,
  DEFECT_SCORE_MIN,
  DEFECT_SCORE_MAX,
  PERFECT_SCORE_MIN,
  PERFECT_SCORE_MAX,
} from './RoastConstants';
import KnowledgeLevelRules from './KnowledgeLevelRules';
import RawRoastEvaluator from './RawRoastEvaluator';
import BurnedRoastEvaluator from './BurnedRoastEvaluator';
import MaillardStagnationEvaluator from './MaillardStagnationEvaluator';
import PerfectRoastEvaluator from './PerfectRoastEvaluator';
import ChargeTemperaturePenaltyCalculator from './ChargeTemperaturePenaltyCalculator';

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
      chargeTemperature,
    } = finishedSim;

    // Penalización por temperatura de carga fuera del rango recomendado
    // (180-200°C): se aplica sobre cualquier resultado, no solo el
    // Perfecto — cargar demasiado frío o caliente afecta el tueste
    // completo, no solo su desenlace final.
    const chargeTempPenalty = ChargeTemperaturePenaltyCalculator.computePenalty(chargeTemperature);

    if (RawRoastEvaluator.isRaw(firstCrackReached, finalTemperature)) {
      const baseScore = RawRoastEvaluator.computeBaseScore(finalTemperature);
      const score = clampDefectScore(baseScore, rules.defectScoreAdjustment - chargeTempPenalty);
      return { score, result: RESULTS.RAW };
    }

    if (BurnedRoastEvaluator.isBurned(burnedFlag, finalTemperature)) {
      const baseScore = BurnedRoastEvaluator.computeBaseScore(maxConsecutiveBurnSeconds);
      const score = clampDefectScore(baseScore, rules.defectScoreAdjustment - chargeTempPenalty);
      return { score, result: RESULTS.BURNED };
    }

    if (maillardStagnationFlag) {
      const baseScore = MaillardStagnationEvaluator.computeBaseScore(maillardStagnationSeconds);
      const score = clampDefectScore(baseScore, rules.defectScoreAdjustment - chargeTempPenalty);
      return { score, result: RESULTS.BAKED };
    }

    const developmentSeconds = roastingElapsedSeconds - (firstCrackTimeSeconds || 0);
    const { score: baseScore, tempPenalty, timePenalty, dtrPenalty } = PerfectRoastEvaluator.computeBreakdown(
      finalTemperature,
      roastingElapsedSeconds,
      developmentSeconds,
      rules.penaltyMultiplier
    );
    const score = Math.max(
      PERFECT_SCORE_MIN,
      Math.min(PERFECT_SCORE_MAX, Math.round(baseScore - chargeTempPenalty))
    );
    return {
      score,
      result: RESULTS.PERFECT,
      breakdown: { tempPenalty, timePenalty, dtrPenalty, chargeTempPenalty: Math.round(chargeTempPenalty) },
    };
  }
}
