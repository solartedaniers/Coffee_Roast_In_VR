import { test, beforeEach, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import RoastSimulationSession from '../src/RoastSimulationSession.js';
import { RoastPacingProfile, PHASES, CHARGE_TEMP_DEFAULT_C, TARGET_TEMP_DEFAULT_C } from '../src/RoastDomain.js';

const PREHEAT_MS = RoastPacingProfile.forPhase(PHASES.PREHEAT).tickIntervalMs;
const CHARGED_MS = RoastPacingProfile.forPhase(PHASES.CHARGE_DIP).tickIntervalMs;
const MAX_TICKS = 3600;
const MAX_STORED_TICKS = 3600;

const newSession = (maxStoredTicks = MAX_STORED_TICKS) =>
  new RoastSimulationSession({
    id: 'sim-1',
    userId: 'user-1',
    chargeTemperature: CHARGE_TEMP_DEFAULT_C,
    targetTemperature: TARGET_TEMP_DEFAULT_C,
    maxStoredTicks,
  });

// MockTimers ejecuta un solo salto por llamada cuando un timer programa el siguiente.
const tickTimes = (ms, times) => {
  for (let i = 0; i < times; i += 1) {
    mock.timers.tick(ms);
  }
};

const advanceUntil = (session, isDone) => {
  for (let i = 0; i < MAX_TICKS && !isDone(session.snapshot); i += 1) {
    const { tickIntervalMs } = RoastPacingProfile.forPhase(session.snapshot.phase);
    mock.timers.tick(tickIntervalMs);
  }
  return session.snapshot;
};

let session;

beforeEach(() => {
  mock.timers.enable({ apis: ['setTimeout'] });
  mock.method(Math, 'random', () => 0.5);
  session = newSession();
});

afterEach(() => {
  session.stop();
  mock.timers.reset();
  mock.restoreAll();
});

test('arranca precalentando y avanza un tick por cada intervalo del precalentado', () => {
  assert.equal(session.snapshot.phase, PHASES.PREHEAT);
  assert.equal(session.snapshot.tick, 0);

  mock.timers.tick(PREHEAT_MS - 1);
  assert.equal(session.snapshot.tick, 0);
  mock.timers.tick(1);
  assert.equal(session.snapshot.tick, 1);
  tickTimes(PREHEAT_MS, 3);
  assert.deepEqual(session.ticksAfter(0).map((tick) => tick.tick), [1, 2, 3, 4]);
  assert.deepEqual(session.ticksAfter(2).map((tick) => tick.tick), [3, 4]);
});

test('guarda solo los últimos ticks y un afterTick ya descartado recibe desde el más antiguo', () => {
  session.stop();
  session = newSession(3);
  tickTimes(PREHEAT_MS, 5);

  assert.deepEqual(session.ticksAfter(0).map((tick) => tick.tick), [3, 4, 5]);
  assert.deepEqual(session.ticksAfter(1).map((tick) => tick.tick), [3, 4, 5]);
  assert.deepEqual(session.ticksAfter(3).map((tick) => tick.tick), [4, 5]);
  assert.deepEqual(session.ticksAfter(5), []);
  assert.equal(session.snapshot.tick, 5);
});

test('al cargar el reloj se reinicia con el intervalo de la carga', () => {
  mock.timers.tick(PREHEAT_MS / 2);
  session.charge();
  assert.equal(session.snapshot.phase, PHASES.CHARGE_DIP);

  mock.timers.tick(CHARGED_MS - 1);
  assert.equal(session.snapshot.tick, 0);
  mock.timers.tick(1);
  assert.equal(session.snapshot.tick, 1);
  assert.equal(session.snapshot.roastSeconds, 1);
});

test('el gas nuevo corre desde el tick siguiente', () => {
  session.setGas(80);
  mock.timers.tick(PREHEAT_MS);

  assert.equal(session.snapshot.gasPercent, 80);
});

test('rechaza cargar dos veces y descargar antes del punto de retorno', () => {
  assert.throws(() => session.discharge({ knowledgeLevel: 'BEGINNER', alerts: [] }), { code: 'SIMULATION_INVALID_PHASE' });
  session.charge();
  assert.throws(() => session.charge(), { code: 'SIMULATION_INVALID_PHASE' });
  assert.throws(() => session.discharge({ knowledgeLevel: 'BEGINNER', alerts: [] }), { code: 'SIMULATION_INVALID_PHASE' });
});

test('no deja descargar en el mismo tick del punto de retorno, cuando el pico todavía no cuenta', () => {
  advanceUntil(session, (s) => s.phase === PHASES.READY);
  session.charge();
  const turningPoint = advanceUntil(session, (s) => s.phase === PHASES.ROASTING);

  assert.throws(() => session.discharge({ knowledgeLevel: 'BEGINNER', alerts: [] }), { code: 'SIMULATION_INVALID_PHASE' });
  mock.timers.tick(CHARGED_MS);
  const request = session.discharge({ knowledgeLevel: 'BEGINNER', alerts: [] });

  assert.equal(request.totalDurationSeconds, turningPoint.roastSeconds + 1);
  assert.ok(request.peakTemperature >= request.finalTemperature);
});

test('al descargar deja de avanzar y arma la sesión con las alertas que sonaron', () => {
  advanceUntil(session, (s) => s.phase === PHASES.READY);
  session.charge();
  const cracked = advanceUntil(session, (s) => s.firstCrack.reached);
  const alerts = [{ id: 'FIRST_CRACK', t: cracked.firstCrack.roastSeconds }];

  const request = session.discharge({ knowledgeLevel: 'BEGINNER', alerts });
  tickTimes(CHARGED_MS, 5);

  assert.equal(session.snapshot.phase, PHASES.FINISHED);
  assert.equal(session.snapshot.tick, cracked.tick);
  assert.equal(request.totalDurationSeconds, cracked.roastSeconds);
  assert.equal(request.firstCrackReached, true);
  assert.deepEqual(request.roastTrace.alerts, alerts);
  assert.throws(() => session.setGas(50), { code: 'SIMULATION_INVALID_PHASE' });
});

test('el estado público no expone la temperatura interna ni los umbrales de los cracks', () => {
  advanceUntil(session, (s) => s.phase === PHASES.READY);
  session.charge();
  tickTimes(CHARGED_MS, 3);
  const keys = Object.keys(session.snapshot);

  assert.ok(!keys.includes('firstCrackThresholdTemp'));
  assert.ok(!keys.includes('secondCrackThresholdTemp'));
  assert.ok(!keys.includes('beanProfile'));
  assert.ok(!keys.includes('samples'));
});
