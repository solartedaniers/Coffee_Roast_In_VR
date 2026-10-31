import TurningPointDetector from './TurningPointDetector';
import { TURNING_POINT_MIN_RISE_C } from './RoastConstants';

test('mientras la sonda sigue bajando no hay punto de retorno', () => {
  expect(TurningPointDetector.hasReachedTurningPoint(120, 120)).toBe(false);
});

test('una subida menor al mínimo todavía no cuenta', () => {
  expect(TurningPointDetector.hasReachedTurningPoint(120 + TURNING_POINT_MIN_RISE_C / 2, 120)).toBe(false);
});

test('al subir el mínimo sobre la menor lectura se marca el punto de retorno', () => {
  expect(TurningPointDetector.hasReachedTurningPoint(120 + TURNING_POINT_MIN_RISE_C, 120)).toBe(true);
});
