import {
  CHARGE_TEMP_MIN_C,
  CHARGE_TEMP_MAX_C,
  CHARGE_TEMP_DEFAULT_C,
  TARGET_TEMP_MIN_C,
  TARGET_TEMP_MAX_C,
  TARGET_TEMP_DEFAULT_C,
  FLAME_POWER_MIN_PCT,
  FLAME_POWER_MAX_PCT,
} from './RoastDomain.js';
import SimulationError from './SimulationError.js';

const USER_ID_PATTERN = /^[\w-]{1,64}$/;
const NON_NEGATIVE_INTEGER_PATTERN = /^\d+$/;

const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);

const readTemperature = (value, min, max, fallback) => {
  if (value === undefined || value === null) {
    return fallback;
  }
  if (!isNumber(value) || value < min || value > max) {
    throw SimulationError.invalidRequest();
  }
  return value;
};

const isAlert = (alert) =>
  alert !== null &&
  typeof alert === 'object' &&
  typeof alert.id === 'string' &&
  alert.id.length > 0 &&
  Number.isInteger(alert.t) &&
  alert.t >= 0;

// Valida lo que llega del backend antes de tocar una simulación. Los rangos son los mismos de la web.
export default class SimulationRequestReader {
  static userId(headers) {
    const userId = headers['x-user-id'];
    if (typeof userId !== 'string' || !USER_ID_PATTERN.test(userId)) {
      throw SimulationError.invalidRequest();
    }
    return userId;
  }

  static settings(body) {
    return {
      chargeTemperature: readTemperature(body.chargeTemperature, CHARGE_TEMP_MIN_C, CHARGE_TEMP_MAX_C, CHARGE_TEMP_DEFAULT_C),
      targetTemperature: readTemperature(body.targetTemperature, TARGET_TEMP_MIN_C, TARGET_TEMP_MAX_C, TARGET_TEMP_DEFAULT_C),
    };
  }

  static afterTick(searchParams) {
    const raw = searchParams.get('afterTick');
    if (raw === null) {
      return 0;
    }
    if (!NON_NEGATIVE_INTEGER_PATTERN.test(raw)) {
      throw SimulationError.invalidRequest();
    }
    return Number(raw);
  }

  static gasPercent(body) {
    if (!isNumber(body.gasPercent)) {
      throw SimulationError.invalidRequest();
    }
    if (body.gasPercent < FLAME_POWER_MIN_PCT || body.gasPercent > FLAME_POWER_MAX_PCT) {
      throw SimulationError.gasOutOfRange();
    }
    return body.gasPercent;
  }

  static discharge(body) {
    const { knowledgeLevel = null, alerts = [] } = body;
    if (knowledgeLevel !== null && typeof knowledgeLevel !== 'string') {
      throw SimulationError.invalidRequest();
    }
    if (!Array.isArray(alerts) || !alerts.every(isAlert)) {
      throw SimulationError.invalidRequest();
    }
    return { knowledgeLevel, alerts: alerts.map(({ id, t }) => ({ id, t })) };
  }
}
