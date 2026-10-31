import RoastSimulationEngine from './RoastSimulationEngine';
import RoastQualityEvaluator from './RoastQualityEvaluator';
import { PHASES, OPERATION_MODES, CHARGE_TEMP_DEFAULT_C, TARGET_TEMP_DEFAULT_C } from './RoastConstants';

const MAX_TICKS = 3600;
const SETTINGS = {
  chargeTemperature: CHARGE_TEMP_DEFAULT_C,
  targetTemperature: TARGET_TEMP_DEFAULT_C,
  operationMode: OPERATION_MODES.MANUAL,
};

const tickUntil = (state, isDone) => {
  let current = state;
  for (let i = 0; i < MAX_TICKS && !isDone(current); i += 1) {
    current = RoastSimulationEngine.tick(current);
  }
  return current;
};

describe('RoastSimulationEngine', () => {
  beforeEach(() => {
    jest.spyOn(Math, 'random').mockReturnValue(0.5);
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('no avanza si el tostador no está encendido o ya se descargó', () => {
    const idle = RoastSimulationEngine.createInitialState(SETTINGS);
    const finished = RoastSimulationEngine.discharge(idle);

    expect(RoastSimulationEngine.tick(idle)).toBe(idle);
    expect(RoastSimulationEngine.tick(finished)).toBe(finished);
  });

  test('precalienta hasta LISTO sin contar tiempo de tueste', () => {
    const ready = tickUntil(RoastSimulationEngine.startPreheat(SETTINGS), (s) => s.phase === PHASES.READY);

    expect(ready.phase).toBe(PHASES.READY);
    expect(ready.airTemperature).toBeGreaterThanOrEqual(CHARGE_TEMP_DEFAULT_C);
    expect(ready.probeTemperature).toBe(ready.airTemperature);
    expect(ready.roastingElapsedSeconds).toBe(0);
    expect(ready.samples).toHaveLength(ready.elapsedSeconds);
  });

  test('al cargar toma la temperatura real del aire y llega al punto de retorno', () => {
    const ready = tickUntil(RoastSimulationEngine.startPreheat(SETTINGS), (s) => s.phase === PHASES.READY);
    const charged = RoastSimulationEngine.charge(ready);

    expect(charged.phase).toBe(PHASES.CHARGE_DIP);
    expect(charged.chargeTemperature).toBe(ready.airTemperature);
    expect(charged.chargeStartElapsedSeconds).toBe(ready.elapsedSeconds);
    expect(charged.firstCrackThresholdTemp).not.toBeNull();

    const roasting = tickUntil(charged, (s) => s.phase === PHASES.ROASTING);
    expect(roasting.phase).toBe(PHASES.ROASTING);
    expect(roasting.probeTemperature).toBeGreaterThan(roasting.lowestProbeSinceCharge);
  });

  test('no deja descargar en el tick del punto de retorno hasta que el pico cuenta', () => {
    const ready = tickUntil(RoastSimulationEngine.startPreheat(SETTINGS), (s) => s.phase === PHASES.READY);
    const charged = RoastSimulationEngine.charge(ready);
    const turningPoint = tickUntil(charged, (s) => s.phase === PHASES.ROASTING);

    expect(RoastSimulationEngine.canDischarge(charged)).toBe(false);
    expect(RoastSimulationEngine.canDischarge(turningPoint)).toBe(false);
    expect(RoastSimulationEngine.canDischarge(RoastSimulationEngine.tick(turningPoint))).toBe(true);
  });

  test('cada muestra guarda el gas con el que corrió ese segundo', () => {
    const preheating = RoastSimulationEngine.tick(RoastSimulationEngine.startPreheat(SETTINGS));
    const next = RoastSimulationEngine.tick(RoastSimulationEngine.setFlamePower(preheating, 80));

    expect(next.samples[next.samples.length - 1].gas).toBe(80);
    expect(next.samples[0].gas).toBe(preheating.flamePowerPercent);
  });

  test('un tueste completo arma la sesión que se guarda con su traza', () => {
    const ready = tickUntil(RoastSimulationEngine.startPreheat(SETTINGS), (s) => s.phase === PHASES.READY);
    const cracked = tickUntil(RoastSimulationEngine.charge(ready), (s) => s.firstCrackReached);
    expect(cracked.firstCrackReached).toBe(true);

    const developed = tickUntil(cracked, (s) => s.roastingElapsedSeconds >= cracked.roastingElapsedSeconds + 60);
    const finished = RoastSimulationEngine.discharge(developed);
    const score = RoastQualityEvaluator.evaluate(finished, 'BEGINNER');
    const request = RoastSimulationEngine.buildSessionRequest(finished, score);

    expect(finished.phase).toBe(PHASES.FINISHED);
    expect(request.finalTemperature).toBe(developed.probeTemperature);
    expect(request.totalDurationSeconds).toBe(developed.roastingElapsedSeconds);
    expect(request.developmentTimeSeconds).toBe(60);
    expect(request.qualityScore).toBe(score.score);
    expect(request.roastTrace.events).toContainEqual({ type: 'DISCHARGE', t: request.totalDurationSeconds });
  });
});
