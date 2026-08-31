import {
  AMBIENT_TEMP_C,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C,
  CHARGE_DIP_DURATION_BASE_SEC,
  CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT,
  CHARGE_DIP_FLOOR_AIR_WEIGHT,
  MAX_SAFE_TEMP_C,
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

  // Dónde "toca fondo" el grano al cargar — promedio ponderado entre el
  // aire al cargar (variable principal, CHARGE_DIP_FLOOR_AIR_WEIGHT) y la
  // temperatura de entrada del grano (variable secundaria): un ambiente
  // caliente amortigua el choque térmico, así que el grano nunca "siente"
  // del todo lo frío que estaba. Clamp de seguridad en MAX_SAFE_TEMP_C —
  // sin techo natural propio como antes, porque el piso ahora crece
  // proporcional al aire sin límite.
  static computeEffectiveChargeFloor(airTempAtCharge, entryTempC) {
    const raw = CHARGE_DIP_FLOOR_AIR_WEIGHT * airTempAtCharge + (1 - CHARGE_DIP_FLOOR_AIR_WEIGHT) * entryTempC;
    return Math.min(MAX_SAFE_TEMP_C, raw);
  }
}
