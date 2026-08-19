import { EVAP_COOLING_FACTOR_C_PER_PCT } from './RoastConstants';
import MoistureModel from './MoistureModel';

// ================================================================
// EvaporativeCoolingTerm
// Estrategia de calor (contrato computeRatePerSecond(context)): calor
// que el grano pierde al evaporar su humedad libre (Q_evap de la
// ecuación 3.2). Reutiliza la tasa de pérdida de MoistureModel en vez
// de recalcularla — un solo dueño de esa fórmula.
// ================================================================
export default class EvaporativeCoolingTerm {
  static computeRatePerSecond({ beanTemp, moisturePct }) {
    const moistureLossRate = MoistureModel.computeMoistureLossRatePerSecond(beanTemp, moisturePct);
    return EVAP_COOLING_FACTOR_C_PER_PCT * moistureLossRate;
  }
}
