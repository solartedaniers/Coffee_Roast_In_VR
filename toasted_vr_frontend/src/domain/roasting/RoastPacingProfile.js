import {
  PHASES,
  AIR_RESPONSE_RATE_PER_SEC,
  PREHEAT_AIR_RESPONSE_RATE_PER_SEC,
  PREHEAT_TICK_INTERVAL_MS,
  CHARGE_DIP_TICK_INTERVAL_MS,
  ROASTING_TICK_INTERVAL_MS,
} from './RoastConstants';

// ================================================================
// RoastPacingProfile
// Responsabilidad única: qué tan rápido responde el aire y cada
// cuánto corre el reloj real, según la fase actual. El
// precalentamiento tiene su propio ritmo (física más lenta y
// realista, reloj real comprimido); carga y tueste no cambian de lo
// ya validado en sesiones anteriores.
// ================================================================
export default class RoastPacingProfile {
  static forPhase(phase) {
    if (phase === PHASES.PREHEAT) {
      return { airResponseRatePerSec: PREHEAT_AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: PREHEAT_TICK_INTERVAL_MS };
    }
    if (phase === PHASES.CHARGE_DIP) {
      return { airResponseRatePerSec: AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: CHARGE_DIP_TICK_INTERVAL_MS };
    }
    return { airResponseRatePerSec: AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: ROASTING_TICK_INTERVAL_MS };
  }
}
