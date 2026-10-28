// ================================================================
// KnowledgeLevelRules
// Responsabilidad única: mapear el nivel de conocimiento declarado
// por el usuario a los ajustes de puntuación/guía que usa el resto
// de la simulación.
// ================================================================

// dtrToleranceRatio: margen a cada lado del DTR ideal (18-22 %) antes de
// penalizar. Parámetro de diseño del simulador: los niveles más altos
// califican más estricto y ADVANCED usa exactamente 18-22 %.
const RULES_BY_LEVEL = Object.freeze({
  BEGINNER: {
    guidanceMode: 'complete',
    penaltyMultiplier: 0.78,
    defectScoreAdjustment: 8,
    dtrToleranceRatio: 0.03,
  },
  INTERMEDIATE: {
    guidanceMode: 'focused',
    penaltyMultiplier: 1,
    defectScoreAdjustment: 0,
    dtrToleranceRatio: 0.02,
  },
  ADVANCED: {
    guidanceMode: 'hidden',
    penaltyMultiplier: 1.28,
    defectScoreAdjustment: -8,
    dtrToleranceRatio: 0,
  },
});

export default class KnowledgeLevelRules {
  static forLevel(knowledgeLevel) {
    return RULES_BY_LEVEL[knowledgeLevel] || RULES_BY_LEVEL.INTERMEDIATE;
  }
}
