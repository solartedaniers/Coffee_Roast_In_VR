import { EVAP_THRESHOLD_TEMP_C, EVAP_RATE_CONSTANT_PER_SEC, MOISTURE_FLOOR_PCT } from './RoastConstants';

// ================================================================
// MoistureModel
// Responsabilidad única: cuánta humedad libre pierde el grano por
// segundo (ecuación 3.3) y cuál es su humedad restante. Es el dueño
// de la fórmula de pérdida — EvaporativeCoolingTerm solo la reutiliza
// para convertirla en el término de enfriamiento Q_evap.
// ================================================================
export default class MoistureModel {
  static computeMoistureLossRatePerSecond(beanTemp, moisturePct) {
    if (moisturePct <= MOISTURE_FLOOR_PCT || beanTemp <= EVAP_THRESHOLD_TEMP_C) return 0;
    return EVAP_RATE_CONSTANT_PER_SEC * moisturePct * (beanTemp - EVAP_THRESHOLD_TEMP_C);
  }

  static computeNextMoisture(currentMoisturePct, beanTemp) {
    const lossRate = MoistureModel.computeMoistureLossRatePerSecond(beanTemp, currentMoisturePct);
    return parseFloat(Math.max(MOISTURE_FLOOR_PCT, currentMoisturePct - lossRate).toFixed(3));
  }
}
