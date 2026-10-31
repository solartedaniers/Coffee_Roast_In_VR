import fs from 'fs';
import path from 'path';
import ChargeDipCalculator from './ChargeDipCalculator';
import RoastThermalModel from './RoastThermalModel';
import ProbeTemperatureModel from './ProbeTemperatureModel';
import TurningPointDetector from './TurningPointDetector';
import {
  AMBIENT_TEMP_C,
  BEAN_DENSITY_REFERENCE,
  BEAN_MOISTURE_REFERENCE_PCT,
  BATCH_WEIGHT_MIN_KG,
  BATCH_WEIGHT_MAX_KG,
  REFERENCE_BATCH_WEIGHT_KG,
  FLAME_POWER_DEFAULT_PCT,
  FIRST_CRACK_TEMP_MIN_C,
} from './RoastConstants';

// ================================================================
// ExpertCurveCalibration.test.js
// Compara la curva que marca la sonda del simulador con los dos
// tuestes del experto (calibration/expert-roasts.csv). Copia la forma
// de la curva y sus tiempos, no las temperaturas del final: la sonda
// del experto lee más bajo que los rangos de la industria que usan
// las reglas.
// ================================================================

// Margen sobre el punto de retorno del experto: sus lecturas son
// enteras y cada 5 s, así que el segundo exacto del retorno se corre.
const TURNING_POINT_TOLERANCE_SECONDS = 30;

function loadExpertRoasts() {
  const [, ...lines] = fs
    .readFileSync(path.join(__dirname, 'calibration/expert-roasts.csv'), 'utf8')
    .trim()
    .split(/\r?\n/);
  const roasts = new Map();
  lines.forEach((line) => {
    const [roast, seconds, probe] = line.split(',').map(Number);
    if (!roasts.has(roast)) roasts.set(roast, []);
    roasts.get(roast).push({ seconds, probe });
  });
  return [...roasts.values()];
}

function turningPointSecond(readings) {
  let lowest = readings[0].probe;
  for (const { seconds, probe } of readings) {
    lowest = Math.min(lowest, probe);
    if (TurningPointDetector.hasReachedTurningPoint(probe, lowest)) return seconds;
  }
  return null;
}

function steepestDropPerSecond(readings) {
  return Math.max(
    ...readings.slice(1).map((reading, index) => (readings[index].probe - reading.probe) / (reading.seconds - readings[index].seconds))
  );
}

// Misma secuencia que el tick de RoastingSimulation desde la carga: aire,
// grano, humedad y sonda, con la carga terminando en el punto de retorno.
function simulateFromCharge(chargeTemp, batchWeightKg) {
  const thermalMassFactor = REFERENCE_BATCH_WEIGHT_KG / batchWeightKg;
  let airTemp = chargeTemp;
  let beanTemp = ChargeDipCalculator.computeEffectiveChargeFloor(chargeTemp, AMBIENT_TEMP_C);
  let moisturePct = BEAN_MOISTURE_REFERENCE_PCT;
  let probeTemp = chargeTemp;
  let lowestProbe = chargeTemp;
  let turningPoint = null;
  const readings = [{ seconds: 0, probe: probeTemp }];

  for (let second = 1; probeTemp < FIRST_CRACK_TEMP_MIN_C; second++) {
    const dipLoss =
      turningPoint == null
        ? ChargeDipCalculator.computeLossPerSecond({
            airTempAtCharge: chargeTemp,
            flamePowerPercent: FLAME_POWER_DEFAULT_PCT,
            secondsSinceCharge: second - 1,
          })
        : 0;
    airTemp = Math.max(
      AMBIENT_TEMP_C,
      RoastThermalModel.computeNextAirTemp(airTemp, FLAME_POWER_DEFAULT_PCT, thermalMassFactor) - dipLoss
    );
    beanTemp = RoastThermalModel.computeNextBeanTemp({
      airTemp,
      beanTemp,
      density: BEAN_DENSITY_REFERENCE,
      moisturePct,
      thermalMassFactor,
      firstCrackReached: false,
    });
    moisturePct = RoastThermalModel.computeNextMoisture(moisturePct, beanTemp);
    probeTemp = ProbeTemperatureModel.computeNextProbeTemp(probeTemp, beanTemp);
    readings.push({ seconds: second, probe: probeTemp });

    lowestProbe = Math.min(lowestProbe, probeTemp);
    if (turningPoint == null && TurningPointDetector.hasReachedTurningPoint(probeTemp, lowestProbe)) {
      turningPoint = second;
    }
  }
  return { readings, turningPoint };
}

const expertRoasts = loadExpertRoasts();
const expertTurningPoints = expertRoasts.map(turningPointSecond);
const expertSteepestDrop = Math.max(...expertRoasts.map(steepestDropPerSecond));

const cases = expertRoasts.flatMap((readings) =>
  [BATCH_WEIGHT_MIN_KG, BATCH_WEIGHT_MAX_KG].map((batchWeightKg) => [readings[0].probe, batchWeightKg])
);

describe.each(cases)('carga a %i °C con un lote de %i kg', (chargeTemp, batchWeightKg) => {
  const { readings, turningPoint } = simulateFromCharge(chargeTemp, batchWeightKg);

  test('la sonda baja en curva, nunca más rápido que la caída más fuerte del experto', () => {
    expect(steepestDropPerSecond(readings)).toBeLessThanOrEqual(expertSteepestDrop);
  });

  test('el punto de retorno llega cuando en los tuestes del experto', () => {
    expect(turningPoint).toBeGreaterThanOrEqual(Math.min(...expertTurningPoints) - TURNING_POINT_TOLERANCE_SECONDS);
    expect(turningPoint).toBeLessThanOrEqual(Math.max(...expertTurningPoints) + TURNING_POINT_TOLERANCE_SECONDS);
  });

  test('después del punto de retorno la sonda no vuelve a bajar hasta el primer crack', () => {
    const afterTurningPoint = readings.slice(turningPoint);
    afterTurningPoint.slice(1).forEach((reading, index) => {
      expect(reading.probe).toBeGreaterThanOrEqual(afterTurningPoint[index].probe);
    });
  });
});
