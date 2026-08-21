import { MAX_SAFE_TEMP_C } from './RoastConstants';
import ConductiveHeatGainTerm from './ConductiveHeatGainTerm';
import EvaporativeCoolingTerm from './EvaporativeCoolingTerm';
import ExothermicReactionTerm from './ExothermicReactionTerm';

// Estrategias de calor del grano (ecuación 3.2): agregar un término nuevo
// solo requiere una clase más en esta lista, sin tocar las existentes.
const HEAT_TERMS = [
  { term: ConductiveHeatGainTerm, sign: 1 },
  { term: EvaporativeCoolingTerm, sign: -1 },
  { term: ExothermicReactionTerm, sign: 1 },
];

// ================================================================
// BeanHeatTransferCalculator
// Responsabilidad única: sumar las estrategias de calor del grano
// (conducción - evaporación + exotérmico) y avanzar su temperatura un
// paso — el "cuerpo 2" del modelo de dos cuerpos.
// ================================================================
export default class BeanHeatTransferCalculator {
  static computeNextBeanTemp(context) {
    const ratePerSecond = HEAT_TERMS.reduce(
      (total, { term, sign }) => total + sign * term.computeRatePerSecond(context),
      0
    );
    // Sin piso en AMBIENT_TEMP_C: la conducción ya se autolimita (persigue
    // al aire, que nunca baja de ahí), y el piso de carga en frío
    // (ChargeDipCalculator.computeEffectiveChargeFloor) puede arrancar por
    // debajo del ambiente a propósito — forzarlo de vuelta aquí borraba
    // ese efecto en un solo tick.
    const next = context.beanTemp + ratePerSecond;
    return parseFloat(Math.min(MAX_SAFE_TEMP_C, next).toFixed(2));
  }
}
