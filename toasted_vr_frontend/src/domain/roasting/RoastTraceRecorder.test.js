import RoastTraceRecorder from './RoastTraceRecorder';
import { ROAST_TRACE_MAX_SAMPLES, ROAST_TRACE_MAX_GAS_CHANGES, ROAST_TRACE_SAMPLE_INTERVAL_SECONDS } from './RoastConstants';

const CHARGE_AT = 300;

// Simulación ya descargada: las muestras antes de la carga son del aire y no entran en la traza.
const finishedSim = ({ seconds = 12, gasAt = () => 30, usedAutoMode = false, firstCrack = 8, secondCrack = null } = {}) => ({
  chargeStartElapsedSeconds: CHARGE_AT,
  roastingElapsedSeconds: seconds,
  usedAutoMode,
  firstCrackReached: firstCrack != null,
  firstCrackTimeSeconds: firstCrack,
  secondCrackReached: secondCrack != null,
  secondCrackTimeSeconds: secondCrack,
  samples: [
    { time: CHARGE_AT - 1, temp: 240, gas: 60 },
    { time: CHARGE_AT, temp: 240, gas: 60 },
    ...Array.from({ length: seconds }, (_, i) => ({ time: CHARGE_AT + i + 1, temp: 100.123 + i, gas: gasAt(i + 1) })),
  ],
});

describe('RoastTraceRecorder', () => {
  test('muestrea cada pocos segundos desde la carga y conserva la primera y la última', () => {
    const trace = RoastTraceRecorder.build(finishedSim());

    expect(trace.version).toBe(1);
    expect(trace.operationMode).toBe('MANUAL');
    expect(trace.sampleIntervalSeconds).toBe(ROAST_TRACE_SAMPLE_INTERVAL_SECONDS);
    expect(trace.samples.map((sample) => sample.t)).toEqual([1, 5, 10, 12]);
    expect(trace.samples[0]).toEqual({ t: 1, bean: 100.12, gas: 30 });
    expect(trace.alerts).toEqual([]);
  });

  test('registra cada cambio de gas del jugador en el segundo en que pasó', () => {
    const trace = RoastTraceRecorder.build(finishedSim({ gasAt: (t) => (t < 4 ? 30 : t < 9 ? 50 : 25) }));

    expect(trace.gasChanges).toEqual([{ t: 4, gas: 50 }, { t: 9, gas: 25 }]);
  });

  test('marca la carga, los cracks y la descarga con el reloj del tueste', () => {
    const trace = RoastTraceRecorder.build(finishedSim({ firstCrack: 8, secondCrack: 11 }));

    expect(trace.events).toEqual([
      { type: 'CHARGE', t: 0 },
      { type: 'FIRST_CRACK', t: 8 },
      { type: 'SECOND_CRACK', t: 11 },
      { type: 'DISCHARGE', t: 12 },
    ]);
    expect(RoastTraceRecorder.build(finishedSim({ firstCrack: null })).events.map((event) => event.type))
      .toEqual(['CHARGE', 'DISCHARGE']);
  });

  test('en automático no manda cambios de gas: no son decisiones del jugador', () => {
    const trace = RoastTraceRecorder.build(finishedSim({ usedAutoMode: true, gasAt: (t) => 20 + t }));

    expect(trace.operationMode).toBe('AUTO');
    expect(trace.gasChanges).toEqual([]);
  });

  test('no arma traza sin carga, con menos de dos muestras o fuera de los límites del servidor', () => {
    expect(RoastTraceRecorder.build({ ...finishedSim(), chargeStartElapsedSeconds: null })).toBeNull();
    expect(RoastTraceRecorder.build(finishedSim({ seconds: 1, firstCrack: null }))).toBeNull();
    const tooLong = ROAST_TRACE_MAX_SAMPLES * ROAST_TRACE_SAMPLE_INTERVAL_SECONDS;
    expect(RoastTraceRecorder.build(finishedSim({ seconds: tooLong, firstCrack: null }))).toBeNull();
    const fidgety = (t) => (t % 2 === 0 ? 30 : 31);
    expect(RoastTraceRecorder.build(finishedSim({ seconds: ROAST_TRACE_MAX_GAS_CHANGES + 2, gasAt: fidgety }))).toBeNull();
  });
});
