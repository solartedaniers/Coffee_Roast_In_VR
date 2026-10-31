import RoastThermalModel from './RoastThermalModel';
import FirstCrackDetector from './FirstCrackDetector';
import {
  AMBIENT_TEMP_C,
  FLAME_POWER_DEFAULT_PCT,
  BEAN_DENSITY_REFERENCE,
  BEAN_MOISTURE_REFERENCE_PCT,
  FIRST_CRACK_TEMP_MIN_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  IDEAL_TOTAL_ROAST_MINUTES,
  AUTO_MODE_BASE_POWER_PCT,
  AUTO_MODE_MAX_POWER_PCT,
} from './RoastConstants';

// Corre el modelo de dos cuerpos tick a tick con un lote dado (masa
// térmica 1, densidad/humedad configurables) a la potencia de arranque
// (50 %), sin carga ni sonda: mide el grano directo. Es una prueba de
// regresión de las constantes físicas; la ventana del primer crack que ve
// el jugador por la sonda la prueba ExpertCurveCalibration.test.js.
// Tras el first crack baja al 20 %, como el experto.
const DEVELOPMENT_PHASE_POWER_PCT = 20;

function runReferenceRoast(totalSeconds, { density = BEAN_DENSITY_REFERENCE, moisture0Pct = BEAN_MOISTURE_REFERENCE_PCT } = {}) {
  const thermalMassFactor = 1;
  const firstCrackThresholdTemp = FirstCrackDetector.pickThresholdTemperature(density);

  let airTemp = AMBIENT_TEMP_C;
  let beanTemp = AMBIENT_TEMP_C;
  let moisturePct = moisture0Pct;
  let firstCrackReached = false;
  let firstCrackTimeSeconds = null;

  for (let second = 1; second <= totalSeconds; second++) {
    const flamePowerPercent = firstCrackReached ? DEVELOPMENT_PHASE_POWER_PCT : FLAME_POWER_DEFAULT_PCT;
    airTemp = RoastThermalModel.computeNextAirTemp(airTemp, flamePowerPercent, thermalMassFactor);
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
  }

  return { airTemp, beanTemp, moisturePct, firstCrackTimeSeconds, firstCrackThresholdTemp };
}

// Rango medido con el umbral en sus dos extremos (196 y 205 °C): 7:17-8:21.
test('el grano del lote de referencia a 50 % llega al first crack entre 7 y 9 minutos', () => {
  const { firstCrackTimeSeconds } = runReferenceRoast(25 * 60);

  expect(firstCrackTimeSeconds).not.toBeNull();
  expect(firstCrackTimeSeconds).toBeGreaterThanOrEqual(7 * 60);
  expect(firstCrackTimeSeconds).toBeLessThanOrEqual(9 * 60);
});

test('a los 12-15 minutos (ventana de tueste típica) el grano está en tueste, no crudo ni quemado', () => {
  const { beanTemp } = runReferenceRoast(IDEAL_TOTAL_ROAST_MINUTES * 60);

  expect(beanTemp).toBeGreaterThanOrEqual(FIRST_CRACK_TEMP_MIN_C);
  expect(beanTemp).toBeLessThan(BURN_ABSOLUTE_CEILING_TEMP_C);
});

// El first crack no debe caer casi siempre en el mismo tiempo: un grano
// poco denso/poco húmedo debe crujir notoriamente antes que uno denso y
// húmedo, a la misma potencia. Umbrales fijos (no aleatorios, a
// diferencia de las otras pruebas) para que el margen no dependa de la
// suerte del sorteo — mismo estilo que ya usa FirstCrackDetector.test.js
// para probar el desplazamiento por densidad.
test('el first crack varía de forma notoria según densidad y humedad del grano', () => {
  const lowDensityFastMoist = runReferenceRoast(25 * 60, { density: 0.65, moisture0Pct: 8 });
  const highDensitySlowMoist = runReferenceRoast(25 * 60, { density: 0.85, moisture0Pct: 12 });

  expect(lowDensityFastMoist.firstCrackTimeSeconds).not.toBeNull();
  expect(highDensitySlowMoist.firstCrackTimeSeconds).not.toBeNull();

  const spreadSeconds = highDensitySlowMoist.firstCrackTimeSeconds - lowDensityFastMoist.firstCrackTimeSeconds;
  expect(spreadSeconds).toBeGreaterThanOrEqual(120);
});

test('el modo automático pide su techo cuando la temperatura está lejos del objetivo', () => {
  expect(RoastThermalModel.computeAutoFlamePowerPercent(120, 200, 10)).toBe(AUTO_MODE_MAX_POWER_PCT);
});

test('en el objetivo y sin subida, la base del modo automático deja el aire sobre el inicio del rango del primer crack', () => {
  const power = RoastThermalModel.computeAutoFlamePowerPercent(200, 200, 0);
  expect(power).toBe(AUTO_MODE_BASE_POWER_PCT);

  let airTemp = AMBIENT_TEMP_C;
  for (let second = 0; second < 120; second++) airTemp = RoastThermalModel.computeNextAirTemp(airTemp, power, 1);
  expect(airTemp).toBeGreaterThan(FIRST_CRACK_TEMP_MIN_C);
});
