import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { setTimeout as sleep } from 'node:timers/promises';
import SimulationRegistry from '../src/SimulationRegistry.js';
import SimulationHttpServer from '../src/SimulationHttpServer.js';
import { RoastPacingProfile, PHASES, CHARGE_TEMP_DEFAULT_C, TARGET_TEMP_DEFAULT_C } from '../src/RoastDomain.js';

const MAX_BODY_BYTES = 1024;
const IDLE_TIMEOUT_MS = 60000;
const PREHEAT_MS = RoastPacingProfile.forPhase(PHASES.PREHEAT).tickIntervalMs;

let registry;
let server;
let baseUrl;

const call = async (method, path, { userId = 'user-1', body, rawBody } = {}) => {
  const headers = { 'Content-Type': 'application/json' };
  if (userId) {
    headers['X-User-Id'] = userId;
  }
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: rawBody ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  const text = await response.text();
  return { status: response.status, json: text ? JSON.parse(text) : null };
};

const createSimulation = async (userId = 'user-1') => (await call('POST', '/api/v1/simulations', { userId, body: {} })).json;

before(async () => {
  registry = new SimulationRegistry({ idleTimeoutMs: IDLE_TIMEOUT_MS });
  server = new SimulationHttpServer({ registry, maxBodyBytes: MAX_BODY_BYTES });
  const { port } = await server.listen(0, '127.0.0.1');
  baseUrl = `http://127.0.0.1:${port}`;
});

after(async () => {
  registry.closeAll();
  await server.close();
});

test('crea la simulación precalentando con los valores por defecto de la web', async () => {
  const { status, json } = await call('POST', '/api/v1/simulations', { body: {} });

  assert.equal(status, 201);
  assert.match(json.simulationId, /^[0-9a-f-]{36}$/);
  assert.equal(json.state.phase, PHASES.PREHEAT);
  assert.equal(json.state.chargeTemperature, CHARGE_TEMP_DEFAULT_C);
  assert.equal(json.state.targetTemperature, TARGET_TEMP_DEFAULT_C);
});

test('rechaza pedidos sin jugador, con temperaturas fuera de rango o con JSON roto', async () => {
  assert.deepEqual(await call('POST', '/api/v1/simulations', { userId: null, body: {} }), {
    status: 400,
    json: { code: 'SIMULATION_INVALID_REQUEST' },
  });
  assert.equal((await call('POST', '/api/v1/simulations', { body: { chargeTemperature: 999 } })).status, 400);
  assert.equal((await call('POST', '/api/v1/simulations', { rawBody: '{roto' })).status, 400);
  assert.equal((await call('POST', '/api/v1/simulations', { rawBody: '[]' })).status, 400);
});

test('rechaza un cuerpo más grande que el máximo', async () => {
  const { simulationId } = await createSimulation();
  const { status, json } = await call('PUT', `/api/v1/simulations/${simulationId}/gas`, {
    rawBody: JSON.stringify({ gasPercent: 50, padding: 'x'.repeat(MAX_BODY_BYTES) }),
  });

  assert.equal(status, 413);
  assert.equal(json.code, 'SIMULATION_BODY_TOO_LARGE');
});

test('devuelve los ticks que avanzó solo desde el que pide el cliente', async () => {
  const { simulationId } = await createSimulation();
  await sleep(PREHEAT_MS * 3);

  const all = await call('GET', `/api/v1/simulations/${simulationId}?afterTick=0`);
  const lastTick = all.json.state.tick;
  const none = await call('GET', `/api/v1/simulations/${simulationId}?afterTick=${lastTick + 1000}`);

  assert.equal(all.status, 200);
  assert.ok(lastTick >= 1);
  assert.equal(all.json.ticks[0].tick, 1);
  assert.deepEqual(none.json.ticks, []);
  assert.equal((await call('GET', `/api/v1/simulations/${simulationId}?afterTick=-1`)).status, 400);
});

test('solo el dueño ve su simulación', async () => {
  const { simulationId } = await createSimulation('user-1');
  const { status, json } = await call('GET', `/api/v1/simulations/${simulationId}`, { userId: 'user-2' });

  assert.equal(status, 404);
  assert.equal(json.code, 'SIMULATION_NOT_FOUND');
});

test('cambia el gas y valida su rango', async () => {
  const { simulationId } = await createSimulation();
  const path = `/api/v1/simulations/${simulationId}/gas`;

  assert.equal((await call('PUT', path, { body: { gasPercent: 70 } })).json.state.gasPercent, 70);
  assert.deepEqual((await call('PUT', path, { body: { gasPercent: 101 } })).json, { code: 'GAS_OUT_OF_RANGE' });
  assert.equal((await call('PUT', path, { body: { gasPercent: '70' } })).status, 400);
});

test('carga, no deja descargar antes del punto de retorno y aborta', async () => {
  const { simulationId } = await createSimulation();
  const path = `/api/v1/simulations/${simulationId}`;

  assert.equal((await call('POST', `${path}/charge`)).json.state.phase, PHASES.CHARGE_DIP);
  assert.equal((await call('POST', `${path}/charge`)).status, 409);
  assert.deepEqual(await call('POST', `${path}/discharge`, { body: { knowledgeLevel: 'BEGINNER', alerts: [] } }), {
    status: 409,
    json: { code: 'SIMULATION_INVALID_PHASE' },
  });
  assert.equal((await call('POST', `${path}/discharge`, { body: { alerts: [{ id: 'X', t: -1 }] } })).status, 400);
  assert.equal((await call('DELETE', path)).status, 204);
  assert.equal((await call('GET', path)).status, 404);
});

test('responde 404 en rutas que no existen', async () => {
  assert.deepEqual(await call('GET', '/api/v1/otra'), { status: 404, json: { code: 'ROUTE_NOT_FOUND' } });
  assert.equal((await call('PATCH', '/api/v1/simulations')).status, 404);
});
