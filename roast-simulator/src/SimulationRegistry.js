import { randomUUID } from 'node:crypto';
import RoastSimulationSession from './RoastSimulationSession.js';
import SimulationError from './SimulationError.js';

// Las simulaciones vivas: una por jugador, solo visibles para su dueño, y se borran solas si nadie las
// consulta durante idleTimeoutMs (por ejemplo, si Unity se cerró sin abortar).
export default class SimulationRegistry {
  #entries = new Map();
  #idleTimeoutMs;

  constructor({ idleTimeoutMs }) {
    this.#idleTimeoutMs = idleTimeoutMs;
  }

  create(userId, settings) {
    for (const [id, entry] of this.#entries) {
      if (entry.session.userId === userId) {
        this.#evict(id);
      }
    }
    const session = new RoastSimulationSession({ id: randomUUID(), userId, ...settings });
    const entry = { session, idleTimer: null };
    this.#entries.set(session.id, entry);
    this.#touch(entry);
    return session;
  }

  find(id, userId) {
    const entry = this.#entries.get(id);
    if (!entry || entry.session.userId !== userId) {
      throw SimulationError.notFound();
    }
    this.#touch(entry);
    return entry.session;
  }

  remove(id, userId) {
    this.find(id, userId);
    this.#evict(id);
  }

  closeAll() {
    for (const id of [...this.#entries.keys()]) {
      this.#evict(id);
    }
  }

  #touch(entry) {
    clearTimeout(entry.idleTimer);
    entry.idleTimer = setTimeout(() => this.#evict(entry.session.id), this.#idleTimeoutMs);
    entry.idleTimer.unref?.();
  }

  #evict(id) {
    const entry = this.#entries.get(id);
    if (!entry) {
      return;
    }
    clearTimeout(entry.idleTimer);
    entry.session.stop();
    this.#entries.delete(id);
  }
}
