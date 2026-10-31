const DEFAULTS = Object.freeze({
  host: '127.0.0.1',
  port: 8083,
  idleTimeoutSeconds: 120,
  maxBodyBytes: 16384,
  maxStoredTicks: 3600,
});

const readPositiveInteger = (env, name, fallback) => {
  const raw = env[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} debe ser un entero positivo y vale "${raw}"`);
  }
  return value;
};

// Configuración del simulador desde variables de entorno (o el .env que carga npm start).
export default class SimulatorConfig {
  static fromEnv(env = process.env) {
    return Object.freeze({
      host: env.SIMULATOR_HOST || DEFAULTS.host,
      port: readPositiveInteger(env, 'SIMULATOR_PORT', DEFAULTS.port),
      idleTimeoutSeconds: readPositiveInteger(env, 'SIMULATION_IDLE_TIMEOUT_SECONDS', DEFAULTS.idleTimeoutSeconds),
      maxBodyBytes: readPositiveInteger(env, 'SIMULATOR_MAX_BODY_BYTES', DEFAULTS.maxBodyBytes),
      maxStoredTicks: readPositiveInteger(env, 'SIMULATION_MAX_STORED_TICKS', DEFAULTS.maxStoredTicks),
    });
  }
}
