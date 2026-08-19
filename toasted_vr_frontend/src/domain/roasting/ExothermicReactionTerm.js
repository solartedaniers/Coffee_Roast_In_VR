import { EXO_HEAT_RATE_C_PER_SEC } from './RoastConstants';

// ================================================================
// ExothermicReactionTerm
// Estrategia de calor (contrato computeRatePerSecond(context)): calor
// propio que libera el grano una vez pasado el first crack (Q_exo de
// la ecuación 3.2) — mantiene la curva subiendo aunque baje la
// potencia del quemador, como en un tueste real.
// ================================================================
export default class ExothermicReactionTerm {
  static computeRatePerSecond({ firstCrackReached }) {
    return firstCrackReached ? EXO_HEAT_RATE_C_PER_SEC : 0;
  }
}
