import ChargeDipCalculator from './ChargeDipCalculator';
import RoastThermalModel from './RoastThermalModel';
import FirstCrackDetector from './FirstCrackDetector';
import {
  AMBIENT_TEMP_C,
  BEAN_DENSITY_REFERENCE,
  BEAN_MOISTURE_REFERENCE_PCT,
  FIRST_CRACK_TEMP_MIN_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  IDEAL_TOTAL_ROAST_MAX_MINUTES,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_TEMP_MIN_C,
  CHARGE_TEMP_MAX_C,
} from './RoastConstants';

// ================================================================
// ChargeAndRoastPipeline.test.js
// A diferencia de RoastThermalModel.test.js (que arranca el grano y el
// aire directo en AMBIENT_TEMP_C, sin fase de carga), esto sí simula la
// CARGA real: el piso de ChargeDipCalculator.computeEffectiveChargeFloor,
// la caída del aire, y la transición a tueste — usando las mismas clases
// de producción, sin reimplementar fórmulas aparte. Sirve de alarma: si
// AIR_WEIGHT (u otra constante de la carga) cambia y desplaza el first
// crack de forma inesperada, este test lo marca en rojo.
//
// Mide el grano, no la sonda. Con el grano entrando a su temperatura
// (CHARGE_DIP_FLOOR_AIR_WEIGHT = 0) la temperatura de carga casi no mueve
// el primer crack: el aire alcanza su objetivo en segundos.
// ================================================================
const DEVELOPMENT_PHASE_POWER_PCT = 20;

function runChargeAndRoast(airTempAtCharge, flamePowerPercent, maxSeconds, { entryTempC = AMBIENT_TEMP_C, density = BEAN_DENSITY_REFERENCE, moisture0Pct = BEAN_MOISTURE_REFERENCE_PCT } = {}) {
  const thermalMassFactor = 1;
  const firstCrackThresholdTemp = FirstCrackDetector.pickThresholdTemperature(density);
  const duration = ChargeDipCalculator.computeEffectiveDurationSeconds(flamePowerPercent);

  let airTemp = airTempAtCharge;
  let beanTemp = ChargeDipCalculator.computeEffectiveChargeFloor(airTempAtCharge, entryTempC);
  let moisturePct = moisture0Pct;
  let firstCrackReached = false;
  let firstCrackTimeSeconds = null;
  let beanAtIdealEnd = null;

  for (let second = 1; second <= maxSeconds; second++) {
    const inChargeDip = second <= duration;
    const power = firstCrackReached ? DEVELOPMENT_PHASE_POWER_PCT : flamePowerPercent;

    const dipLoss = inChargeDip
      ? ChargeDipCalculator.computeLossPerSecond({ airTempAtCharge, flamePowerPercent: power, secondsSinceCharge: second - 1 })
      : 0;
    airTemp = Math.max(AMBIENT_TEMP_C, RoastThermalModel.computeNextAirTemp(airTemp, power, thermalMassFactor) - dipLoss);

    beanTemp = RoastThermalModel.computeNextBeanTemp({
      airTemp,
      beanTemp,
      density,
      moisturePct,
      thermalMassFactor,
      firstCrackReached,
    });
    moisturePct = RoastThermalModel.computeNextMoisture(moisturePct, beanTemp);

    if (!firstCrackReached && FirstCrackDetector.hasReachedFirstCrack(beanTemp, firstCrackThresholdTemp)) {
      firstCrackReached = true;
      firstCrackTimeSeconds = second;
    }
    if (second === IDEAL_TOTAL_ROAST_MAX_MINUTES * 60) beanAtIdealEnd = beanTemp;
  }

  return { beanTemp, firstCrackTimeSeconds, beanAtIdealEnd };
}

// Rango medido con el umbral en sus dos extremos: 7:13-8:20 en todas las cargas.
test.each([
  [CHARGE_TEMP_MIN_C, 7, 9],
  [180, 7, 9],
  [200, 7, 9],
  [CHARGE_TEMP_MAX_C, 7, 9],
])(
  'carga a %i°C: first crack cae entre %i y %i minutos (alarma si esto se mueve)',
  (airTempAtCharge, minMinutes, maxMinutes) => {
    const { firstCrackTimeSeconds } = runChargeAndRoast(airTempAtCharge, FLAME_POWER_DEFAULT_PCT, 20 * 60);
    const minutes = firstCrackTimeSeconds / 60;

    // eslint-disable-next-line no-console
    console.log(`carga a ${airTempAtCharge}°C -> first crack a los ${minutes.toFixed(2)} min`);

    expect(firstCrackTimeSeconds).not.toBeNull();
    expect(minutes).toBeGreaterThanOrEqual(minMinutes);
    expect(minutes).toBeLessThanOrEqual(maxMinutes);
  }
);

test('incluso con la carga más alta permitida, el grano al final de la duración ideal sigue en tueste, no crudo ni quemado', () => {
  const { beanAtIdealEnd } = runChargeAndRoast(CHARGE_TEMP_MAX_C, FLAME_POWER_DEFAULT_PCT, 20 * 60);

  expect(beanAtIdealEnd).toBeGreaterThanOrEqual(FIRST_CRACK_TEMP_MIN_C);
  expect(beanAtIdealEnd).toBeLessThan(BURN_ABSOLUTE_CEILING_TEMP_C);
});
