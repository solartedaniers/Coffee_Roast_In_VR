import {
  OPERATION_MODES,
  ROAST_TRACE_VERSION,
  ROAST_TRACE_SAMPLE_INTERVAL_SECONDS,
  ROAST_TRACE_MAX_SAMPLES,
  ROAST_TRACE_MAX_GAS_CHANGES,
  ROAST_TRACE_MAX_EVENTS,
} from './RoastConstants';

const roundTo2 = (value) => Math.round(value * 100) / 100;

// ================================================================
// RoastTraceRecorder
// Responsabilidad única: armar al descargar la traza que califica la IA
// (contrato v1, IA/docs/unity-roast-trace-contract.md) desde las muestras
// por segundo de la simulación. t es el segundo del tueste (0 en la carga),
// el mismo reloj de totalDurationSeconds y del first crack.
// ================================================================
export default class RoastTraceRecorder {
  // null si no hay tueste que calificar o si la traza no cabe en los límites
  // del servidor: la sesión se guarda igual, calificada solo con las reglas.
  static build(sim) {
    if (sim.chargeStartElapsedSeconds == null) return null;
    const totalSeconds = Math.max(1, Math.round(sim.roastingElapsedSeconds));
    const perSecond = sim.samples
      .map((sample) => ({ t: sample.time - sim.chargeStartElapsedSeconds, bean: sample.temp, gas: sample.gas }))
      .filter((sample) => sample.t >= 1 && sample.t <= totalSeconds && sample.gas != null);

    const lastIndex = perSecond.length - 1;
    // La primera muestra siempre va: de ella sale el gas con el que se cargó.
    const samples = perSecond
      .filter((sample, index) => index === 0 || index === lastIndex || sample.t % ROAST_TRACE_SAMPLE_INTERVAL_SECONDS === 0)
      .map((sample) => ({ t: sample.t, bean: roundTo2(sample.bean), gas: sample.gas }));
    // En automático el gas lo mueve el control, no el jugador: no hay decisiones que calificar.
    const operationMode = sim.usedAutoMode ? OPERATION_MODES.AUTO : OPERATION_MODES.MANUAL;
    const gasChanges = sim.usedAutoMode ? [] : RoastTraceRecorder.gasChanges(perSecond);
    const events = RoastTraceRecorder.events(sim, totalSeconds);

    if (
      samples.length < 2 ||
      samples.length > ROAST_TRACE_MAX_SAMPLES ||
      gasChanges.length > ROAST_TRACE_MAX_GAS_CHANGES ||
      events.length > ROAST_TRACE_MAX_EVENTS
    ) {
      return null;
    }
    return {
      version: ROAST_TRACE_VERSION,
      operationMode,
      sampleIntervalSeconds: ROAST_TRACE_SAMPLE_INTERVAL_SECONDS,
      samples,
      gasChanges,
      events,
      // Las alertas con id del contrato las manda Unity; la web todavía no las emite.
      alerts: [],
    };
  }

  static gasChanges(perSecond) {
    return perSecond
      .filter((sample, index) => index > 0 && sample.gas !== perSecond[index - 1].gas)
      .map((sample) => ({ t: sample.t, gas: sample.gas }));
  }

  static events(sim, totalSeconds) {
    const events = [{ type: 'CHARGE', t: 0 }];
    if (sim.firstCrackReached && sim.firstCrackTimeSeconds != null) {
      events.push({ type: 'FIRST_CRACK', t: sim.firstCrackTimeSeconds });
    }
    if (sim.secondCrackReached && sim.secondCrackTimeSeconds != null) {
      events.push({ type: 'SECOND_CRACK', t: sim.secondCrackTimeSeconds });
    }
    events.push({ type: 'DISCHARGE', t: totalSeconds });
    return events;
  }
}
