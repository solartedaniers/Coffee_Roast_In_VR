import { AMBIENT_TEMP_C, RAW_TEMP_CEILING_C, RAW_SCORE_MAX } from './RoastConstants';

// ================================================================
// RawRoastEvaluator
// Responsabilidad única: decidir si un tueste quedó crudo (sin
// desarrollarse lo suficiente) y puntuarlo.
// ================================================================
export default class RawRoastEvaluator {
  static isRaw(firstCrackReached, finalTemperature) {
    return !firstCrackReached || finalTemperature < RAW_TEMP_CEILING_C;
  }

  static computeBaseScore(finalTemperature) {
    const progress = Math.max(0, (finalTemperature - AMBIENT_TEMP_C) / (RAW_TEMP_CEILING_C - AMBIENT_TEMP_C));
    return Math.round(progress * RAW_SCORE_MAX);
  }
}
