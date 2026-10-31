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

  constructor({ id, userId, chargeTemperature, targetTemperature }) {
    this.id = id;
    this.userId = userId;
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

  // ponytail: guarda todos los ticks en memoria (~300 B cada uno); una sesión que se queda horas en
  // LISTO crece sin tope hasta que expira por inactividad. Poner un máximo si llega a importar.
  ticksAfter(tick) {
    return this.#ticks.slice(Math.max(0, tick));
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

  discharge({ knowledgeLevel, alerts }) {
    if (this.#state.phase !== PHASES.ROASTING) {
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
    this.#scheduleNextTick();
  }
}
