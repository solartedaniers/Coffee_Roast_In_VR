import {
  BURN_ABSOLUTE_CEILING_TEMP_C,
  BURN_CONSECUTIVE_LIMIT_SEC,
  BURN_SCORE_BASE,
  BURN_SCORE_PENALTY_PER_EXTRA_SEC,
  DEFECT_SCORE_MIN,
} from './RoastConstants';

// ================================================================
// BurnedRoastEvaluator
// Responsabilidad única: decidir si un tueste quedó quemado — ya sea
// por exceder el tiempo tolerado en zona de riesgo (BurnRiskMonitor,
// en tiempo real) o por superar la temperatura final absoluta al
// momento de descargar — y puntuarlo.
// ================================================================
export default class BurnedRoastEvaluator {
  static isBurned(burnedFlag, finalTemperature) {
    return burnedFlag || finalTemperature > BURN_ABSOLUTE_CEILING_TEMP_C;
  }

  static computeBaseScore(maxConsecutiveBurnSeconds) {
    const overrunSeconds = Math.max(0, maxConsecutiveBurnSeconds - BURN_CONSECUTIVE_LIMIT_SEC);
    const penalty = overrunSeconds * BURN_SCORE_PENALTY_PER_EXTRA_SEC;
    return Math.max(DEFECT_SCORE_MIN, Math.round(BURN_SCORE_BASE - penalty));
  }
}
