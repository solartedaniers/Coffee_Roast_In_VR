import { test } from 'node:test';
import assert from 'node:assert/strict';
import SimulatorConfig from '../src/SimulatorConfig.js';

test('sin variables escucha solo en 127.0.0.1:8083', () => {
  assert.deepEqual({ ...SimulatorConfig.fromEnv({}) }, {
    host: '127.0.0.1',
    port: 8083,
    idleTimeoutSeconds: 120,
    maxBodyBytes: 16384,
    maxStoredTicks: 3600,
  });
});

test('toma los valores de las variables de entorno', () => {
  const config = SimulatorConfig.fromEnv({
    SIMULATOR_HOST: '127.0.0.2',
    SIMULATOR_PORT: '9000',
    SIMULATION_IDLE_TIMEOUT_SECONDS: '30',
    SIMULATOR_MAX_BODY_BYTES: '2048',
    SIMULATION_MAX_STORED_TICKS: '600',
  });

  assert.equal(config.host, '127.0.0.2');
  assert.equal(config.port, 9000);
  assert.equal(config.idleTimeoutSeconds, 30);
  assert.equal(config.maxBodyBytes, 2048);
  assert.equal(config.maxStoredTicks, 600);
});

test('falla al arrancar con un número inválido', () => {
  assert.throws(() => SimulatorConfig.fromEnv({ SIMULATOR_PORT: 'abc' }), /SIMULATOR_PORT/);
  assert.throws(() => SimulatorConfig.fromEnv({ SIMULATION_IDLE_TIMEOUT_SECONDS: '0' }), /SIMULATION_IDLE_TIMEOUT_SECONDS/);
  assert.throws(() => SimulatorConfig.fromEnv({ SIMULATION_MAX_STORED_TICKS: '-5' }), /SIMULATION_MAX_STORED_TICKS/);
});
