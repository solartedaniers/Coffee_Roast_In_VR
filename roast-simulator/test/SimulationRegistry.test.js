import { test, beforeEach, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import SimulationRegistry from '../src/SimulationRegistry.js';

const IDLE_TIMEOUT_MS = 120000;
const SETTINGS = { chargeTemperature: 190, targetTemperature: 210 };
const TICK_STEP_MS = 250;

// MockTimers ejecuta un solo salto por llamada cuando un timer programa el siguiente.
const tickTimes = (ms, times) => {
  for (let i = 0; i < times; i += 1) {
    mock.timers.tick(ms);
  }
};

let registry;

beforeEach(() => {
  mock.timers.enable({ apis: ['setTimeout'] });
  registry = new SimulationRegistry({ idleTimeoutMs: IDLE_TIMEOUT_MS });
});

afterEach(() => {
  registry.closeAll();
  mock.timers.reset();
});

test('solo el dueño encuentra su simulación', () => {
  const session = registry.create('user-1', SETTINGS);

  assert.equal(registry.find(session.id, 'user-1'), session);
  assert.throws(() => registry.find(session.id, 'user-2'), { code: 'SIMULATION_NOT_FOUND' });
  assert.throws(() => registry.find('otra', 'user-1'), { code: 'SIMULATION_NOT_FOUND' });
});

test('una simulación nueva reemplaza la anterior del mismo jugador y la detiene', () => {
  const first = registry.create('user-1', SETTINGS);
  const other = registry.create('user-2', SETTINGS);
  const second = registry.create('user-1', SETTINGS);
  const frozenTick = first.snapshot.tick;
  tickTimes(TICK_STEP_MS, 20);

  assert.throws(() => registry.find(first.id, 'user-1'), { code: 'SIMULATION_NOT_FOUND' });
  assert.equal(first.snapshot.tick, frozenTick);
  assert.equal(registry.find(second.id, 'user-1'), second);
  assert.equal(registry.find(other.id, 'user-2'), other);
});

test('se borra sola si nadie la consulta y cada consulta reinicia la espera', () => {
  const session = registry.create('user-1', SETTINGS);

  mock.timers.tick(IDLE_TIMEOUT_MS - 1);
  registry.find(session.id, 'user-1');
  mock.timers.tick(IDLE_TIMEOUT_MS - 1);
  assert.equal(registry.find(session.id, 'user-1'), session);

  mock.timers.tick(IDLE_TIMEOUT_MS);
  assert.throws(() => registry.find(session.id, 'user-1'), { code: 'SIMULATION_NOT_FOUND' });
});

test('al abortar se borra y deja de avanzar', () => {
  const session = registry.create('user-1', SETTINGS);

  registry.remove(session.id, 'user-1');
  tickTimes(TICK_STEP_MS, 20);

  assert.equal(session.snapshot.tick, 0);
  assert.throws(() => registry.find(session.id, 'user-1'), { code: 'SIMULATION_NOT_FOUND' });
});
