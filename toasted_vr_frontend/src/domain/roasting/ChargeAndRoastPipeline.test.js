import ChargeDipCalculator from './ChargeDipCalculator';
import RoastThermalModel from './RoastThermalModel';
import FirstCrackDetector from './FirstCrackDetector';
import {
  AMBIENT_TEMP_C,
  BEAN_DENSITY_REFERENCE,
  BEAN_MOISTURE_REFERENCE_PCT,
  FIRST_CRACK_TEMP_MIN_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  IDEAL_TOTAL_ROAST_MINUTES,
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
// Con AIR_WEIGHT=0.5 (ver RoastConstants.js), el first crack YA NO cae
// siempre en la ventana clásica 8-12 min — decisión consciente, priorizando
// el realismo físico del piso de carga sobre esa ventana. Los rangos de
// abajo documentan el comportamiento actual, no la ventana vieja.
// ================================================================
const DEVELOPMENT_PHASE_POWER_PCT = 25;

function runChargeAndRoast(airTempAtCharge, flamePowerPercent, maxSeconds, { entryTempC = AMBIENT_TEMP_C, density = BEAN_DENSITY_REFERENCE, moisture0Pct = BEAN_MOISTURE_REFERENCE_PCT } = {}) {
  const thermalMassFactor = 1;
  const firstCrackThresholdTemp = FirstCrackDetector.pickThresholdTemperature(density);
  const duration = ChargeDipCalculator.computeEffectiveDurationSeconds(flamePowerPercent);

  let airTemp = airTempAtCharge;
  let beanTemp = ChargeDipCalculator.computeEffectiveChargeFloor(airTempAtCharge, entryTempC);
  let moisturePct = moisture0Pct;
  let firstCrackReached = false;
  let firstCrackTimeSeconds = null;
  let beanAt13Min = null;

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
    if (second === IDEAL_TOTAL_ROAST_MINUTES * 60) beanAt13Min = beanTemp;
  }

  return { beanTemp, firstCrackTimeSeconds, beanAt13Min };
}

test.each([
  [100, 8, 11],
  [150, 7, 10],
  [200, 6, 9],
  [264, 4, 7],
  [300, 3, 6],
])(
  'carga a %i°C: first crack cae entre %i y %i minutos (alarma si esto se mueve)',
  (airTempAtCharge, minMinutes, maxMinutes) => {
    const { firstCrackTimeSeconds } = runChargeAndRoast(airTempAtCharge, 30, 20 * 60);
    const minutes = firstCrackTimeSeconds / 60;

    // eslint-disable-next-line no-console
    console.log(`carga a ${airTempAtCharge}°C -> first crack a los ${minutes.toFixed(2)} min`);

    expect(firstCrackTimeSeconds).not.toBeNull();
    expect(minutes).toBeGreaterThanOrEqual(minMinutes);
    expect(minutes).toBeLessThanOrEqual(maxMinutes);
  }
);

test('incluso con la carga más agresiva probada (300°C), el grano a los 13 min sigue en tueste, no crudo ni quemado', () => {
  const { beanAt13Min } = runChargeAndRoast(300, 30, 20 * 60);

  expect(beanAt13Min).toBeGreaterThanOrEqual(FIRST_CRACK_TEMP_MIN_C);
  expect(beanAt13Min).toBeLessThan(BURN_ABSOLUTE_CEILING_TEMP_C);
});
