import { RoastSimulationEngine, RoastQualityEvaluator, RoastPacingProfile, PHASES, OPERATION_MODES } from './RoastDomain.js';
import TickSnapshotPresenter from './TickSnapshotPresenter.js';
import SimulationError from './SimulationError.js';

const CHARGEABLE_PHASES = new Set([PHASES.PREHEAT, PHASES.READY]);

// Un tueste en curso con su propio reloj: avanza solo al ritmo de RoastPacingProfile, igual que el
// setInterval de la web, y guarda cada tick para que el cliente recupere los que se perdió.
export default class RoastSimulationSession {
  #state;
  #ticks = [];
  #timer = null;
  #maxStoredTicks;

  constructor({ id, userId, chargeTemperature, targetTemperature, maxStoredTicks }) {
    this.id = id;
    this.userId = userId;
    this.#maxStoredTicks = maxStoredTicks;
    this.#state = RoastSimulationEngine.startPreheat({
      chargeTemperature,
      targetTemperature,
      operationMode: OPERATION_MODES.MANUAL,
    });
    this.#scheduleNextTick();
  }

  get snapshot() {
    return TickSnapshotPresenter.present(this.#state);
  }

  // Los ticks guardados son consecutivos; si el cliente pide uno que ya se descartó, recibe desde el
  // más antiguo que queda.
  ticksAfter(tick) {
    if (this.#ticks.length === 0) {
      return [];
    }
    const oldestTick = this.#ticks[0].tick;
    return this.#ticks.slice(Math.max(0, tick - oldestTick + 1));
  }

  setGas(gasPercent) {
    this.#requireRunning();
    this.#state = RoastSimulationEngine.setFlamePower(this.#state, gasPercent);
  }

  // Igual que la web: al cargar se reinicia el reloj, así el primer tick cargado llega un intervalo
  // completo de CHARGE_DIP después y no lo que faltaba del precalentado.
  charge() {
    if (!CHARGEABLE_PHASES.has(this.#state.phase)) {
      throw SimulationError.invalidPhase();
    }
    this.#state = RoastSimulationEngine.charge(this.#state);
    this.stop();
    this.#scheduleNextTick();
  }

  // El motor empieza a llevar el pico un tick después del punto de retorno. Descargar justo en ese tick
  // dejaría el pico por debajo de la final y el backend rechazaría la sesión (PeakTemperatureRule).
  discharge({ knowledgeLevel, alerts }) {
    if (!RoastSimulationEngine.canDischarge(this.#state)) {
      throw SimulationError.invalidPhase();
    }
    this.stop();
    this.#state = RoastSimulationEngine.discharge(this.#state);
    const scoreResult = RoastQualityEvaluator.evaluate(this.#state, knowledgeLevel);
    const sessionRequest = RoastSimulationEngine.buildSessionRequest(this.#state, scoreResult);
    sessionRequest.roastTrace.alerts = alerts;
    return sessionRequest;
  }

  stop() {
    clearTimeout(this.#timer);
    this.#timer = null;
  }

  #requireRunning() {
    if (!RoastSimulationEngine.isRunning(this.#state.phase)) {
      throw SimulationError.invalidPhase();
    }
  }

  #scheduleNextTick() {
    if (!RoastSimulationEngine.isRunning(this.#state.phase)) {
      return;
    }
    const { tickIntervalMs } = RoastPacingProfile.forPhase(this.#state.phase);
    this.#timer = setTimeout(() => this.#advance(), tickIntervalMs);
    this.#timer.unref?.();
  }

  #advance() {
    this.#state = RoastSimulationEngine.tick(this.#state);
    this.#ticks.push(TickSnapshotPresenter.present(this.#state));
    if (this.#ticks.length > this.#maxStoredTicks) {
      this.#ticks.shift();
    }
    this.#scheduleNextTick();
  }
}
