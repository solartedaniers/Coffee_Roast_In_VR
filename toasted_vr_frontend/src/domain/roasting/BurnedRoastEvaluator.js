import {
  BURN_ABSOLUTE_CEILING_TEMP_C,
  BURN_SCORE_BASE,
  BURN_SCORE_PENALTY_PER_EXTRA_SEC,
  DEFECT_SCORE_MIN,
} from './RoastConstants';

// ================================================================
// BurnedRoastEvaluator
// Responsabilidad única: decidir si un tueste quedó quemado — por la
// marca de BurnRiskMonitor (en tiempo real) o porque la temperatura
// máxima pasó del techo del tueste medio — y puntuarlo.
// ================================================================
export default class BurnedRoastEvaluator {
  static isBurned(burnedFlag, peakTemperature) {
    return burnedFlag || peakTemperature > BURN_ABSOLUTE_CEILING_TEMP_C;
  }

  static computeBaseScore(maxConsecutiveSecondsAboveCeiling) {
    const penalty = maxConsecutiveSecondsAboveCeiling * BURN_SCORE_PENALTY_PER_EXTRA_SEC;
    return Math.max(DEFECT_SCORE_MIN, Math.round(BURN_SCORE_BASE - penalty));
  }
}
