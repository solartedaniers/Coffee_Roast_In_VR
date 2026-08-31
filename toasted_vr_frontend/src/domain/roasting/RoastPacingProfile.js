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
// realista, reloj real comprimido); LISTO usa el mismo ritmo — el
// aire sigue respondiendo a la potencia después de alcanzar la
// temperatura de carga, solo se enciende la alerta visual, la física
// no se detiene. Carga y tueste no cambian de lo ya validado en
// sesiones anteriores.
// ================================================================
export default class RoastPacingProfile {
  static forPhase(phase) {
    if (phase === PHASES.PREHEAT || phase === PHASES.READY) {
      return { airResponseRatePerSec: PREHEAT_AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: PREHEAT_TICK_INTERVAL_MS };
    }
    if (phase === PHASES.CHARGE_DIP) {
      return { airResponseRatePerSec: AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: CHARGE_DIP_TICK_INTERVAL_MS };
    }
    return { airResponseRatePerSec: AIR_RESPONSE_RATE_PER_SEC, tickIntervalMs: ROASTING_TICK_INTERVAL_MS };
  }
}
