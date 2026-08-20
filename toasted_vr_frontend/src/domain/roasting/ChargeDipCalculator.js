import {
  AMBIENT_TEMP_C,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C,
  CHARGE_DIP_DURATION_BASE_SEC,
  CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT,
} from './RoastConstants';

// ================================================================
// ChargeDipCalculator
// Responsabilidad única: la caída de temperatura del aire al cargar
// el café frío en el tambor caliente — ya no es un valor fijo, depende
// de qué tan caliente estaba el aire al momento de la carga y de la
// potencia de llama en cada instante. computeEffectiveDurationSeconds
// es el mismo cálculo que usa la transición de fase CARGA→TUESTE en
// RoastingSimulation.js, para que el "punto de recuperación" de la
// caída y el cambio de fase siempre coincidan, incluso si la potencia
// cambia en vivo durante la carga.
// ================================================================
export default class ChargeDipCalculator {
  static computeEffectiveDurationSeconds(flamePowerPercent) {
    const effectivePower = Math.max(CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT, flamePowerPercent);
    return CHARGE_DIP_DURATION_BASE_SEC * (FLAME_POWER_DEFAULT_PCT / effectivePower);
  }

  static computeLossPerSecond({ airTempAtCharge, flamePowerPercent, secondsSinceCharge }) {
    const peakLoss = CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C * (airTempAtCharge - AMBIENT_TEMP_C);
    const duration = ChargeDipCalculator.computeEffectiveDurationSeconds(flamePowerPercent);
    const progress = Math.min(1, secondsSinceCharge / duration);
    return Math.max(0, peakLoss * (1 - progress));
  }
}
