import {
  MAILLARD_STAGNATION_LIMIT_SEC,
  BAKED_SCORE_AT_THRESHOLD,
  BAKED_SCORE_FLOOR,
  BAKED_SCORE_PENALTY_PER_EXTRA_SEC,
} from './RoastConstants';

// ================================================================
// MaillardStagnationEvaluator
// Responsabilidad única: puntuar un tueste "horneado" (grano
// estancado en la fase de Maillard) de forma proporcional a cuánto
// tiempo se mantuvo estancado más allá del umbral que dispara el
// defecto, en vez de una nota fija.
// ================================================================
export default class MaillardStagnationEvaluator {
  static computeBaseScore(stagnationSeconds) {
    const extraSeconds = Math.max(0, stagnationSeconds - MAILLARD_STAGNATION_LIMIT_SEC);
    const penalty = extraSeconds * BAKED_SCORE_PENALTY_PER_EXTRA_SEC;
    return Math.max(BAKED_SCORE_FLOOR, Math.round(BAKED_SCORE_AT_THRESHOLD - penalty));
  }
}
