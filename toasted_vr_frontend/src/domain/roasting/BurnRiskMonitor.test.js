import BurnRiskMonitor from './BurnRiskMonitor';
import { ROR_SCORCH_THRESHOLD_C_PER_MIN, MOISTURE_SCORCH_THRESHOLD_PCT, GUIDE_FAST_RISE_C_PER_MIN } from './RoastConstants';

test('el segundo crack quema aunque no pase del techo', () => {
  expect(BurnRiskMonitor.isBurned(220, true)).toBe(true);
});

test('sobre 200°C y bajo el techo, sin segundo crack, no quema', () => {
  expect(BurnRiskMonitor.isBurned(212, false)).toBe(false);
  expect(BurnRiskMonitor.isBurned(220.1, false)).toBe(true);
});

test('solo cuenta los segundos seguidos sobre el techo', () => {
  expect(BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(4, 210)).toBe(0);
  expect(BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(4, 221)).toBe(5);
});

test('una subida muy agresiva con el grano seco lo quema', () => {
  expect(BurnRiskMonitor.isScorchedByRateAndMoisture(ROR_SCORCH_THRESHOLD_C_PER_MIN, MOISTURE_SCORCH_THRESHOLD_PCT)).toBe(true);
  expect(BurnRiskMonitor.isScorchedByRateAndMoisture(ROR_SCORCH_THRESHOLD_C_PER_MIN - 1, MOISTURE_SCORCH_THRESHOLD_PCT)).toBe(false);
  expect(BurnRiskMonitor.isScorchedByRateAndMoisture(ROR_SCORCH_THRESHOLD_C_PER_MIN, MOISTURE_SCORCH_THRESHOLD_PCT + 1)).toBe(false);
});

test('la subida que la guía todavía acepta no quema por subida agresiva', () => {
  expect(BurnRiskMonitor.isScorchedByRateAndMoisture(GUIDE_FAST_RISE_C_PER_MIN, 0)).toBe(false);
});
