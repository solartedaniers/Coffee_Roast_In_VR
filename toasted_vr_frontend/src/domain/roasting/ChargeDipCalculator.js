import {
  AMBIENT_TEMP_C,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C,
  CHARGE_DIP_DURATION_BASE_SEC,
  CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT,
  CHARGE_DIP_FLOOR_CENTER_C,
  CHARGE_DIP_FLOOR_SLOPE_C_PER_DEGREE,
  CHARGE_DIP_COLD_EXTRA_DROP_MAX_C,
  CHARGE_DIP_HOT_CUSHION_MAX_C,
} from './RoastConstants';

// ================================================================
// ChargeDipCalculator
// Responsabilidad única: todo lo que depende de qué tan caliente
// estaba el aire al momento de cargar el café frío en el tambor —
// tanto la caída de temperatura del aire como el piso real del grano.
// computeEffectiveDurationSeconds es el mismo cálculo que usa la
// transición de fase CARGA→TUESTE en RoastingSimulation.js, para que
// el "punto de recuperación" de la caída y el cambio de fase siempre
// coincidan, incluso si la potencia cambia en vivo durante la carga.
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

  // Dónde "toca fondo" el grano al cargar — rampa continua centrada en la
  // zona segura (CHARGE_DIP_FLOOR_CENTER_C), con clamp a cada lado: más
  // fría profundiza el piso (recuperación más lenta), más caliente lo
  // eleva (casi no hay caída, pero llega antes al rango de quemado).
  static computeEffectiveChargeFloor(airTempAtCharge, entryTempC) {
    const raw = CHARGE_DIP_FLOOR_SLOPE_C_PER_DEGREE * (airTempAtCharge - CHARGE_DIP_FLOOR_CENTER_C);
    const clamped = Math.min(CHARGE_DIP_HOT_CUSHION_MAX_C, Math.max(-CHARGE_DIP_COLD_EXTRA_DROP_MAX_C, raw));
    return entryTempC + clamped;
  }
}
