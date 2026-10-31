import { createServer } from 'node:http';
import SimulationError from './SimulationError.js';
import SimulationRequestReader from './SimulationRequestReader.js';

const BASE_PATH = '/api/v1/simulations';
const ID = '([0-9a-f-]{36})';
const INTERNAL_ERROR_CODE = 'SIMULATOR_INTERNAL_ERROR';

// Transporte HTTP del simulador. Solo lo llama el backend, que ya validó el token del jugador y manda
// su id en X-User-Id; por eso escucha en 127.0.0.1 y no en la red.
export default class SimulationHttpServer {
  #registry;
  #maxBodyBytes;
  #server;
  #routes;

  constructor({ registry, maxBodyBytes }) {
    this.#registry = registry;
    this.#maxBodyBytes = maxBodyBytes;
    this.#routes = [
      { method: 'POST', pattern: new RegExp(`^${BASE_PATH}$`), handle: (ctx) => this.#create(ctx) },
      { method: 'GET', pattern: new RegExp(`^${BASE_PATH}/${ID}$`), handle: (ctx) => this.#read(ctx) },
      { method: 'PUT', pattern: new RegExp(`^${BASE_PATH}/${ID}/gas$`), handle: (ctx) => this.#setGas(ctx) },
      { method: 'POST', pattern: new RegExp(`^${BASE_PATH}/${ID}/charge$`), handle: (ctx) => this.#charge(ctx) },
      { method: 'POST', pattern: new RegExp(`^${BASE_PATH}/${ID}/discharge$`), handle: (ctx) => this.#discharge(ctx) },
      { method: 'DELETE', pattern: new RegExp(`^${BASE_PATH}/${ID}$`), handle: (ctx) => this.#remove(ctx) },
    ];
    this.#server = createServer((request, response) => this.#dispatch(request, response));
  }

  listen(port, host) {
    return new Promise((resolve, reject) => {
      this.#server.once('error', reject);
      this.#server.listen(port, host, () => resolve(this.#server.address()));
    });
  }

  close() {
    return new Promise((resolve) => {
      this.#server.close(() => resolve());
      this.#server.closeAllConnections();
    });
  }

  async #dispatch(request, response) {
    try {
      const url = new URL(request.url, 'http://simulator');
      const { route, match } = this.#findRoute(request.method, url.pathname);
      const userId = SimulationRequestReader.userId(request.headers);
      const result = await route.handle({ request, url, userId, simulationId: match[1] });
      this.#send(response, result.status, result.body);
    } catch (error) {
      if (error instanceof SimulationError) {
        this.#send(response, error.status, { code: error.code });
        return;
      }
      console.error('Error inesperado en el simulador:', error);
      this.#send(response, 500, { code: INTERNAL_ERROR_CODE });
    }
  }

  #findRoute(method, pathname) {
    for (const route of this.#routes) {
      const match = route.method === method ? pathname.match(route.pattern) : null;
      if (match) {
        return { route, match };
      }
    }
    throw SimulationError.routeNotFound();
  }

  async #create({ request, userId }) {
    const settings = SimulationRequestReader.settings(await this.#readJson(request));
    const session = this.#registry.create(userId, settings);
    return { status: 201, body: { simulationId: session.id, state: session.snapshot } };
  }

  #read({ url, userId, simulationId }) {
    const afterTick = SimulationRequestReader.afterTick(url.searchParams);
    const session = this.#registry.find(simulationId, userId);
    return { status: 200, body: { state: session.snapshot, ticks: session.ticksAfter(afterTick) } };
  }

  async #setGas({ request, userId, simulationId }) {
    const gasPercent = SimulationRequestReader.gasPercent(await this.#readJson(request));
    const session = this.#registry.find(simulationId, userId);
    session.setGas(gasPercent);
    return { status: 200, body: { state: session.snapshot } };
  }

  #charge({ userId, simulationId }) {
    const session = this.#registry.find(simulationId, userId);
    session.charge();
    return { status: 200, body: { state: session.snapshot } };
  }

  async #discharge({ request, userId, simulationId }) {
    const discharge = SimulationRequestReader.discharge(await this.#readJson(request));
    const session = this.#registry.find(simulationId, userId);
    const sessionRequest = session.discharge(discharge);
    return { status: 200, body: { state: session.snapshot, sessionRequest } };
  }

  #remove({ userId, simulationId }) {
    this.#registry.remove(simulationId, userId);
    return { status: 204, body: null };
  }

  async #readJson(request) {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
      size += chunk.length;
      if (size > this.#maxBodyBytes) {
        throw SimulationError.bodyTooLarge();
      }
      chunks.push(chunk);
    }
    if (size === 0) {
      return {};
    }
    let body;
    try {
      body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch {
      throw SimulationError.invalidRequest();
    }
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      throw SimulationError.invalidRequest();
    }
    return body;
  }

  #send(response, status, body) {
    if (body === null) {
      response.writeHead(status).end();
      return;
    }
    const payload = JSON.stringify(body);
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(payload) });
    response.end(payload);
  }
}
