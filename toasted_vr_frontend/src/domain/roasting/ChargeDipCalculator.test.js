import ChargeDipCalculator from './ChargeDipCalculator';
import { CHARGE_DIP_FLOOR_AIR_WEIGHT, MAX_SAFE_TEMP_C } from './RoastConstants';

const ENTRY_TEMP_C = 20;

test('si el aire al cargar es igual a la entrada del grano, el piso es esa misma temperatura', () => {
  expect(ChargeDipCalculator.computeEffectiveChargeFloor(ENTRY_TEMP_C, ENTRY_TEMP_C)).toBe(ENTRY_TEMP_C);
});

test('promedio ponderado: el piso se reparte entre aire y entrada según CHARGE_DIP_FLOOR_AIR_WEIGHT', () => {
  const airTempAtCharge = 200;
  const expectedFloor =
    CHARGE_DIP_FLOOR_AIR_WEIGHT * airTempAtCharge + (1 - CHARGE_DIP_FLOOR_AIR_WEIGHT) * ENTRY_TEMP_C;

  expect(ChargeDipCalculator.computeEffectiveChargeFloor(airTempAtCharge, ENTRY_TEMP_C)).toBeCloseTo(expectedFloor);
});

// La sonda es la que baja en curva desde el calor del tambor: el grano entra a su temperatura.
test('con el horno a 200°C el grano arranca cerca de su temperatura de entrada', () => {
  const floor = ChargeDipCalculator.computeEffectiveChargeFloor(200, ENTRY_TEMP_C);
  expect(floor).toBeGreaterThanOrEqual(ENTRY_TEMP_C);
  expect(floor).toBeLessThan(ENTRY_TEMP_C + 10);
});

test('el piso nunca baja al subir la temperatura del aire al cargar', () => {
  const floorAt100 = ChargeDipCalculator.computeEffectiveChargeFloor(100, ENTRY_TEMP_C);
  const floorAt200 = ChargeDipCalculator.computeEffectiveChargeFloor(200, ENTRY_TEMP_C);
  const floorAt300 = ChargeDipCalculator.computeEffectiveChargeFloor(300, ENTRY_TEMP_C);

  expect(floorAt100).toBeLessThanOrEqual(floorAt200);
  expect(floorAt200).toBeLessThanOrEqual(floorAt300);
});

test('clamp de seguridad: el piso nunca supera MAX_SAFE_TEMP_C', () => {
  const floor = ChargeDipCalculator.computeEffectiveChargeFloor(MAX_SAFE_TEMP_C + 50, MAX_SAFE_TEMP_C + 50);
  expect(floor).toBe(MAX_SAFE_TEMP_C);
});
