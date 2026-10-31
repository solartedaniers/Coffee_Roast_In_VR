import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  RoastSimulationEngine,
  RoastQualityEvaluator,
  PHASES,
  OPERATION_MODES,
  CHARGE_TEMP_DEFAULT_C,
  TARGET_TEMP_DEFAULT_C,
} from '../src/RoastDomain.js';

const MAX_TICKS = 3600;

const tickUntil = (state, isDone) => {
  let current = state;
  for (let i = 0; i < MAX_TICKS && !isDone(current); i += 1) {
    current = RoastSimulationEngine.tick(current);
  }
  return current;
};

test('el motor de la web corre en Node y completa un tueste con su nota', (t) => {
  t.mock.method(Math, 'random', () => 0.5);
  const preheating = RoastSimulationEngine.startPreheat({
    chargeTemperature: CHARGE_TEMP_DEFAULT_C,
    targetTemperature: TARGET_TEMP_DEFAULT_C,
    operationMode: OPERATION_MODES.MANUAL,
  });

  const ready = tickUntil(preheating, (s) => s.phase === PHASES.READY);
  const cracked = tickUntil(RoastSimulationEngine.charge(ready), (s) => s.firstCrackReached);
  const finished = RoastSimulationEngine.discharge(cracked);
  const score = RoastQualityEvaluator.evaluate(finished, 'BEGINNER');

  assert.equal(ready.phase, PHASES.READY);
  assert.equal(cracked.firstCrackReached, true);
  assert.equal(finished.phase, PHASES.FINISHED);
  assert.ok(Number.isInteger(score.score));
});
