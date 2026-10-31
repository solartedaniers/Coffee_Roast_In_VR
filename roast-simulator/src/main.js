import SimulatorConfig from './SimulatorConfig.js';
import SimulationRegistry from './SimulationRegistry.js';
import SimulationHttpServer from './SimulationHttpServer.js';

const MILLIS_PER_SECOND = 1000;

const config = SimulatorConfig.fromEnv();
const registry = new SimulationRegistry({ idleTimeoutMs: config.idleTimeoutSeconds * MILLIS_PER_SECOND });
const server = new SimulationHttpServer({ registry, maxBodyBytes: config.maxBodyBytes });

await server.listen(config.port, config.host);
console.log(`Simulador de tueste escuchando en http://${config.host}:${config.port}`);

const shutdown = async () => {
  registry.closeAll();
  await server.close();
};
process.once('SIGINT', shutdown);
process.once('SIGTERM', shutdown);
