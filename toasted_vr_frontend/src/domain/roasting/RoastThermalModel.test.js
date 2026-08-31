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
} from './RoastConstants';

// Corre el modelo de dos cuerpos tick a tick con un lote dado (masa
// térmica 1, densidad/humedad configurables) a la potencia "eficiente"
// recomendada (30%). Sirve de prueba de regresión y, a la vez, de
// herramienta de calibración de las constantes físicas: si se ajusta
// AIR_RESPONSE_RATE_PER_SEC/MAX_AIR_TEMP_C/HEAT_TRANSFER_COEFF_PER_SEC,
// este test dice si el resultado sigue cayendo en la ventana realista.
// Potencia reducida tras el first crack: como haría un operador atento en
// la fase de desarrollo (bajar la llama para no pasarse), no un valor de
// dominio — el modelo en sí no reduce la potencia solo, eso es decisión
// del usuario/modo AUTO. 25%, no menos: con MAX_AIR_TEMP_C=750, bajar a
// 20% manda el objetivo del aire a 166°C — por debajo del rango de first
// crack — y el grano se estanca en vez de seguir desarrollando.
const DEVELOPMENT_PHASE_POWER_PCT = 25;

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

// Ventana actualizada junto con FIRST_CRACK_TEMP_MIN_C/MAX_C (196-205°C,
// antes 189-198°C, ver RoastConstants.js): el rango real medido para el
// lote de referencia con el umbral nuevo es 11:37-12:53 (verificado con
// simulación numérica antes de aplicar el cambio).
test('el lote de referencia a potencia eficiente (30%) llega al first crack entre 11 y 13 minutos', () => {
  const { firstCrackTimeSeconds } = runReferenceRoast(25 * 60);

  expect(firstCrackTimeSeconds).not.toBeNull();
  expect(firstCrackTimeSeconds).toBeGreaterThanOrEqual(11 * 60);
  expect(firstCrackTimeSeconds).toBeLessThanOrEqual(13 * 60);
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
