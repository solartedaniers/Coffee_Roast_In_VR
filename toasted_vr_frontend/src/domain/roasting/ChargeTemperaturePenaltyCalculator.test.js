import ChargeTemperaturePenaltyCalculator from './ChargeTemperaturePenaltyCalculator';
import { CHARGE_TEMP_IDEAL_MIN_C, CHARGE_TEMP_IDEAL_MAX_C } from './RoastConstants';

test('sin penalización dentro del rango recomendado', () => {
  expect(ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MIN_C)).toBe(0);
  expect(ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MAX_C)).toBe(0);
  expect(
    ChargeTemperaturePenaltyCalculator.computePenalty((CHARGE_TEMP_IDEAL_MIN_C + CHARGE_TEMP_IDEAL_MAX_C) / 2)
  ).toBe(0);
});

test('penalización creciente fuera del rango, en ambas direcciones', () => {
  const belowNear = ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MIN_C - 5);
  const belowFar = ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MIN_C - 20);
  const aboveNear = ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MAX_C + 5);
  const aboveFar = ChargeTemperaturePenaltyCalculator.computePenalty(CHARGE_TEMP_IDEAL_MAX_C + 20);

  expect(belowNear).toBeGreaterThan(0);
  expect(aboveNear).toBeGreaterThan(0);
  expect(belowFar).toBeGreaterThan(belowNear);
  expect(aboveFar).toBeGreaterThan(aboveNear);
});
