import fs from 'fs';
import path from 'path';
import ChargeDipCalculator from './ChargeDipCalculator';
import RoastThermalModel from './RoastThermalModel';
import ProbeTemperatureModel from './ProbeTemperatureModel';
import TurningPointDetector from './TurningPointDetector';
import FirstCrackDetector from './FirstCrackDetector';
import GreenBeanProfileFactory from './GreenBeanProfileFactory';
import RoastMetrics from './RoastMetrics';
import {
  AMBIENT_TEMP_C,
  BEAN_DENSITY_REFERENCE,
  BEAN_MOISTURE_REFERENCE_PCT,
  BATCH_WEIGHT_MIN_KG,
  BATCH_WEIGHT_MAX_KG,
  REFERENCE_BATCH_WEIGHT_KG,
  CHARGE_TEMP_DEFAULT_C,
  FIRST_CRACK_TEMP_MIN_C,
  FIRST_CRACK_TEMP_MAX_C,
  DTR_IDEAL_MIN_RATIO,
  DTR_IDEAL_MAX_RATIO,
  BURN_WARNING_TEMP_C,
  GUIDE_DRYING_FLAME_PCT,
  GUIDE_MAILLARD_FLAME_PCT,
  GUIDE_DEVELOPMENT_FLAME_PCT,
  GUIDE_STEP_DOWN_TEMP_C,
  GUIDE_FAST_RISE_C_PER_MIN,
  GUIDE_SLOW_RISE_C_PER_MIN,
} from './RoastConstants';

// ================================================================
// ExpertCurveCalibration.test.js
// Compara la curva que marca la sonda del simulador con los dos
// tuestes del experto (calibration/expert-roasts.csv), con el mismo gas
// que usó él, y prueba que la guía de es.json lleva el primer crack a
// la ventana de la rúbrica de la IA. El experto está anclado como lote
// de 6 kg: los lotes más livianos se calientan más rápido.
// ================================================================

// Margen sobre el punto de retorno del experto: sus lecturas son
// enteras y cada 5 s, así que el segundo exacto del retorno se corre.
const TURNING_POINT_TOLERANCE_SECONDS = 30;
// Punto de retorno del experto (98-99 °C) para el lote de 6 kg, y el
// rango que admite cualquier lote: depende de la masa.
const EXPERT_TURNING_POINT_C = 98.5;
const EXPERT_TURNING_POINT_TOLERANCE_C = 3;
const TURNING_POINT_MIN_C = 94;
const TURNING_POINT_MAX_C = 106;
// Temperatura final al descargar con DTR del 20 % siguiendo al experto.
const EXPERT_FINAL_MIN_C = 205;
const EXPERT_FINAL_MAX_C = 212;
// Perfiles aleatorios que deben caer en la ventana siguiendo la guía.
const RANDOM_PROFILE_COUNT = 500;
const RANDOM_PROFILE_SEED = 2024;
const GUIDE_MIN_COVERAGE = 0.9;
const MAX_ROAST_SECONDS = 30 * 60;

const TARGET_DTR = (DTR_IDEAL_MIN_RATIO + DTR_IDEAL_MAX_RATIO) / 2;
const MIDDLE_FIRST_CRACK_C = (FIRST_CRACK_TEMP_MIN_C + FIRST_CRACK_TEMP_MAX_C) / 2;

const rubric = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../../IA/config/roast_rubric.json'), 'utf8'));
const FIRST_CRACK_WINDOW_MIN_SECONDS = rubric.parameters.firstCrackMinMinutes * 60;
const FIRST_CRACK_WINDOW_MAX_SECONDS = rubric.parameters.firstCrackMaxMinutes * 60;

// La última fila de cada tueste es la descarga (el gas vuelve al 50 % para el siguiente lote).
function loadExpertRoasts() {
  const [, ...lines] = fs
    .readFileSync(path.join(__dirname, 'calibration/expert-roasts.csv'), 'utf8')
    .trim()
    .split(/\r?\n/);
  const roasts = new Map();
  lines.forEach((line) => {
    const [roast, seconds, probe, flame] = line.split(',').map(Number);
    if (!roasts.has(roast)) roasts.set(roast, []);
    roasts.get(roast).push({ seconds, probe, flame });
  });
  return [...roasts.values()].map((readings) => readings.slice(0, -1));
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

// El gas que tenía el experto en cada segundo, y después de su última lectura el último que usó.
const expertGas = (readings) => ({ roastSeconds }) =>
  readings.reduce((flame, reading) => (reading.seconds <= roastSeconds ? reading.flame : flame), readings[0].flame);

// Un jugador que sigue la guía de es.json mirando la sonda y el Increm °T.
function guideGas({ probe, rateOfRise, turningPointReached, firstCrackReached }) {
  if (firstCrackReached) return GUIDE_DEVELOPMENT_FLAME_PCT;
  if (!turningPointReached) return GUIDE_DRYING_FLAME_PCT;
  if (probe < GUIDE_STEP_DOWN_TEMP_C) {
    return rateOfRise > GUIDE_FAST_RISE_C_PER_MIN ? GUIDE_MAILLARD_FLAME_PCT : GUIDE_DRYING_FLAME_PCT;
  }
  return rateOfRise < GUIDE_SLOW_RISE_C_PER_MIN ? GUIDE_DRYING_FLAME_PCT : GUIDE_MAILLARD_FLAME_PCT;
}

// Misma secuencia que el tick de RoastingSimulation desde la carga: aire,
// grano, humedad y sonda; la carga termina en el punto de retorno, el
// primer crack se lee en la sonda y se descarga con TARGET_DTR.
function simulateRoast({
  chargeTemp,
  batchWeightKg,
  gasFor,
  density = BEAN_DENSITY_REFERENCE,
  moisturePct: initialMoisturePct = BEAN_MOISTURE_REFERENCE_PCT,
  entryTempC = AMBIENT_TEMP_C,
  firstCrackThresholdTemp = MIDDLE_FIRST_CRACK_C,
}) {
  const thermalMassFactor = REFERENCE_BATCH_WEIGHT_KG / batchWeightKg;
  let airTemp = chargeTemp;
  let beanTemp = ChargeDipCalculator.computeEffectiveChargeFloor(chargeTemp, entryTempC);
  let moisturePct = initialMoisturePct;
  let probeTemp = chargeTemp;
  let lowestProbe = chargeTemp;
  let rateOfRise = 0;
  let turningPoint = null;
  let firstCrack = null;
  const readings = [{ seconds: 0, probe: probeTemp }];
  const samples = [{ time: 0, temp: probeTemp }];

  for (let second = 1; second <= MAX_ROAST_SECONDS; second++) {
    const flamePowerPercent = gasFor({
      roastSeconds: second - 1,
      probe: probeTemp,
      rateOfRise,
      turningPointReached: turningPoint != null,
      firstCrackReached: firstCrack != null,
    });
    const dipLoss =
      turningPoint == null
        ? ChargeDipCalculator.computeLossPerSecond({ airTempAtCharge: chargeTemp, flamePowerPercent, secondsSinceCharge: second - 1 })
        : 0;
    airTemp = Math.max(AMBIENT_TEMP_C, RoastThermalModel.computeNextAirTemp(airTemp, flamePowerPercent, thermalMassFactor) - dipLoss);
    beanTemp = RoastThermalModel.computeNextBeanTemp({
      airTemp,
      beanTemp,
      density,
      moisturePct,
      thermalMassFactor,
      firstCrackReached: firstCrack != null,
    });
    moisturePct = RoastThermalModel.computeNextMoisture(moisturePct, beanTemp);
    probeTemp = ProbeTemperatureModel.computeNextProbeTemp(probeTemp, beanTemp);
    readings.push({ seconds: second, probe: probeTemp });
    samples.push({ time: second, temp: probeTemp });
    rateOfRise = RoastMetrics.computeRateOfRisePerMinute(samples, second);

    if (turningPoint == null) {
      lowestProbe = Math.min(lowestProbe, probeTemp);
      if (TurningPointDetector.hasReachedTurningPoint(probeTemp, lowestProbe)) turningPoint = second;
    } else if (firstCrack == null && FirstCrackDetector.hasReachedFirstCrack(probeTemp, firstCrackThresholdTemp)) {
      firstCrack = second;
    }
    if (firstCrack != null && (second - firstCrack) / second >= TARGET_DTR) break;
  }
  return { readings, turningPoint, turningPointTemp: lowestProbe, firstCrack, finalTemp: probeTemp };
}

const expertRoasts = loadExpertRoasts();
const expertTurningPoints = expertRoasts.map(turningPointSecond);
const expertSteepestDrop = Math.max(...expertRoasts.map(steepestDropPerSecond));
const middleBatchWeightKg = (BATCH_WEIGHT_MIN_KG + BATCH_WEIGHT_MAX_KG) / 2;
const batchWeights = [BATCH_WEIGHT_MIN_KG, middleBatchWeightKg, BATCH_WEIGHT_MAX_KG];

const expertCases = expertRoasts.flatMap((readings, index) =>
  batchWeights.map((batchWeightKg) => [index + 1, readings[0].probe, batchWeightKg, readings])
);

describe.each(expertCases)('gas del tueste %i del experto, carga a %i °C con un lote de %d kg', (roast, chargeTemp, batchWeightKg, expertReadings) => {
  const { readings, turningPoint, turningPointTemp, firstCrack, finalTemp } = simulateRoast({
    chargeTemp,
    batchWeightKg,
    gasFor: expertGas(expertReadings),
  });

  test('la sonda baja en curva, nunca más rápido que la caída más fuerte del experto', () => {
    expect(steepestDropPerSecond(readings)).toBeLessThanOrEqual(expertSteepestDrop);
  });

  test('el punto de retorno llega cuando en los tuestes del experto', () => {
    expect(turningPoint).toBeGreaterThanOrEqual(Math.min(...expertTurningPoints) - TURNING_POINT_TOLERANCE_SECONDS);
    expect(turningPoint).toBeLessThanOrEqual(Math.max(...expertTurningPoints) + TURNING_POINT_TOLERANCE_SECONDS);
  });

  test('el punto de retorno queda en el rango que admite la masa del lote', () => {
    expect(turningPointTemp).toBeGreaterThanOrEqual(TURNING_POINT_MIN_C);
    expect(turningPointTemp).toBeLessThanOrEqual(TURNING_POINT_MAX_C);
    if (batchWeightKg === BATCH_WEIGHT_MAX_KG) {
      expect(Math.abs(turningPointTemp - EXPERT_TURNING_POINT_C)).toBeLessThanOrEqual(EXPERT_TURNING_POINT_TOLERANCE_C);
    }
  });

  test('después del punto de retorno la sonda no vuelve a bajar hasta el primer crack', () => {
    expect(firstCrack).not.toBeNull();
    const untilFirstCrack = readings.slice(turningPoint, firstCrack + 1);
    untilFirstCrack.slice(1).forEach((reading, index) => {
      expect(reading.probe).toBeGreaterThanOrEqual(untilFirstCrack[index].probe);
    });
  });

  test('al descargar con DTR del 20 % la temperatura final queda entre 205 y 212 °C', () => {
    expect(finalTemp).toBeGreaterThanOrEqual(EXPERT_FINAL_MIN_C);
    expect(finalTemp).toBeLessThanOrEqual(EXPERT_FINAL_MAX_C);
    expect(finalTemp).toBeLessThan(BURN_WARNING_TEMP_C);
  });
});

describe('siguiendo la guía de es.json', () => {
  const referenceCases = batchWeights.flatMap((batchWeightKg) =>
    [FIRST_CRACK_TEMP_MIN_C, FIRST_CRACK_TEMP_MAX_C].map((firstCrackThresholdTemp) => [batchWeightKg, firstCrackThresholdTemp])
  );

  test.each(referenceCases)('el grano de referencia en un lote de %d kg (crack a %i °C) cruza en la ventana de la rúbrica', (batchWeightKg, firstCrackThresholdTemp) => {
    const { firstCrack } = simulateRoast({ chargeTemp: CHARGE_TEMP_DEFAULT_C, batchWeightKg, gasFor: guideGas, firstCrackThresholdTemp });
    expect(firstCrack).toBeGreaterThanOrEqual(FIRST_CRACK_WINDOW_MIN_SECONDS);
    expect(firstCrack).toBeLessThanOrEqual(FIRST_CRACK_WINDOW_MAX_SECONDS);
  });

  // Mismo sorteo que una sesión real (GreenBeanProfileFactory y FirstCrackDetector), con semilla fija.
  test('al menos el 90 % de los granos aleatorios cruza en la ventana de la rúbrica', () => {
    let seed = RANDOM_PROFILE_SEED;
    const random = jest.spyOn(Math, 'random').mockImplementation(() => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    });
    const profiles = Array.from({ length: RANDOM_PROFILE_COUNT }, () => {
      const profile = GreenBeanProfileFactory.createRandom();
      return { profile, firstCrackThresholdTemp: FirstCrackDetector.pickThresholdTemperature(profile.density) };
    });
    random.mockRestore();

    const inWindow = profiles.filter(({ profile, firstCrackThresholdTemp }) => {
      const { firstCrack } = simulateRoast({
        chargeTemp: CHARGE_TEMP_DEFAULT_C,
        batchWeightKg: profile.batchWeightKg,
        gasFor: guideGas,
        density: profile.density,
        moisturePct: profile.moisture0Pct,
        entryTempC: profile.entryTempC,
        firstCrackThresholdTemp,
      });
      return firstCrack >= FIRST_CRACK_WINDOW_MIN_SECONDS && firstCrack <= FIRST_CRACK_WINDOW_MAX_SECONDS;
    });
    // eslint-disable-next-line no-console
    console.log(`cobertura de la guía: ${inWindow.length} de ${profiles.length} granos aleatorios`);
    expect(inWindow.length / profiles.length).toBeGreaterThanOrEqual(GUIDE_MIN_COVERAGE);
  });
});
