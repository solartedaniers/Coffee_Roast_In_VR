// ================================================================
// KnowledgeLevelRules
// Responsabilidad única: mapear el nivel de conocimiento declarado
// por el usuario a los ajustes de puntuación/guía que usa el resto
// de la simulación.
// ================================================================

const RULES_BY_LEVEL = Object.freeze({
  BEGINNER: {
    guidanceMode: 'complete',
    penaltyMultiplier: 0.78,
    defectScoreAdjustment: 8,
  },
  INTERMEDIATE: {
    guidanceMode: 'focused',
    penaltyMultiplier: 1,
    defectScoreAdjustment: 0,
  },
  ADVANCED: {
    guidanceMode: 'hidden',
    penaltyMultiplier: 1.28,
    defectScoreAdjustment: -8,
  },
});

export default class KnowledgeLevelRules {
  static forLevel(knowledgeLevel) {
    return RULES_BY_LEVEL[knowledgeLevel] || RULES_BY_LEVEL.INTERMEDIATE;
  }
}
