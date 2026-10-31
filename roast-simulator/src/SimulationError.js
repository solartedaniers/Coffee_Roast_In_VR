// Error que el servidor HTTP traduce a {"code"} con su estado. El backend pone el mensaje en español.
export default class SimulationError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  static notFound() {
    return new SimulationError(404, 'SIMULATION_NOT_FOUND');
  }

  static invalidPhase() {
    return new SimulationError(409, 'SIMULATION_INVALID_PHASE');
  }

  static invalidRequest() {
    return new SimulationError(400, 'SIMULATION_INVALID_REQUEST');
  }

  static gasOutOfRange() {
    return new SimulationError(400, 'GAS_OUT_OF_RANGE');
  }

  static bodyTooLarge() {
    return new SimulationError(413, 'SIMULATION_BODY_TOO_LARGE');
  }

  static routeNotFound() {
    return new SimulationError(404, 'ROUTE_NOT_FOUND');
  }
}
