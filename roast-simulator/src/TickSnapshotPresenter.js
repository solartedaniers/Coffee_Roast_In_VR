import { GrainAppearanceModel, PHASES, BURN_WARNING_TEMP_C, ALARM_LIMIT_DEFAULT_C } from './RoastDomain.js';

const PRE_CHARGE_PHASES = new Set([PHASES.PREHEAT, PHASES.READY]);
const RUNNING_PHASES = new Set([PHASES.PREHEAT, PHASES.READY, PHASES.CHARGE_DIP, PHASES.ROASTING]);

// Lo que ve el jugador en cada tick, con los mismos avisos que la web. Deja fuera la temperatura
// interna del grano y los umbrales sorteados de los cracks para no adelantar el resultado.
export default class TickSnapshotPresenter {
  static present(state) {
    const isPreCharge = PRE_CHARGE_PHASES.has(state.phase);
    return {
      tick: state.elapsedSeconds,
      phase: state.phase,
      roastSeconds: state.roastingElapsedSeconds,
      temperature: state.probeTemperature,
      airTemperature: state.airTemperature,
      moisturePct: state.moisturePct,
      gasPercent: state.flamePowerPercent,
      risePerMinute: state.rateOfRisePerMinute,
      chargeTemperature: state.chargeTemperature,
      targetTemperature: state.targetTemperature,
      grainState: GrainAppearanceModel.getGrainStateName(state.probeTemperature, state.firstCrackReached),
      firstCrack: { reached: state.firstCrackReached, roastSeconds: state.firstCrackTimeSeconds },
      secondCrack: { reached: state.secondCrackReached, roastSeconds: state.secondCrackTimeSeconds },
      alarms: {
        burned: state.burnedFlag,
        baked: state.maillardStagnationFlag,
        burnWarning: state.phase === PHASES.ROASTING && state.probeTemperature >= BURN_WARNING_TEMP_C && !state.burnedFlag,
        alarmLimitExceeded: RUNNING_PHASES.has(state.phase) && state.probeTemperature >= ALARM_LIMIT_DEFAULT_C,
        chargeOvershoot: isPreCharge && state.airTemperature > state.chargeTemperature,
      },
    };
  }
}
