import fs from 'fs';
import path from 'path';
import RoastQualityEvaluator from '../domain/roasting/RoastQualityEvaluator';
import {
  MIN_AIR_TEMP_C,
  MAX_AIR_TEMP_C,
  TARGET_TEMP_MIN_C,
  TARGET_TEMP_MAX_C,
  MAX_SAFE_TEMP_C,
  RAW_TEMP_CEILING_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  DEFECT_SCORE_MIN,
  DEFECT_SCORE_MAX,
  PERFECT_SCORE_MIN,
  PERFECT_SCORE_MAX,
} from '../domain/roasting/RoastConstants';

// RF015: el backend valida las sesiones con sus propios límites
// (app.roast-validation en application.yml). Estos tests fallan si el
// simulador y el servidor dejan de estar de acuerdo.
const BACKEND_DIR = path.resolve(__dirname, '../../../Toasted_VR');
const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

// Lee el valor por defecto de ${ROAST_X:valor} en application.yml.
const readBackendDefault = (property) => {
  const yaml = fs.readFileSync(path.join(BACKEND_DIR, 'src/main/resources/application.yml'), 'utf8');
  const match = yaml.match(new RegExp(`^\\s*${property}:\\s*\\$\\{[A-Z_]+:([-\\d.]+)\\}`, 'm'));
  if (!match) throw new Error(`No se encontró app.roast-validation.${property} en application.yml`);
  return Number(match[1]);
};

describe('límites del servidor sincronizados con RoastConstants', () => {
  test.each([
    ['charge-temperature-max', MAX_AIR_TEMP_C],
    ['target-temperature-min', TARGET_TEMP_MIN_C],
    ['target-temperature-max', TARGET_TEMP_MAX_C],
    ['final-temperature-max', MAX_SAFE_TEMP_C],
    ['raw-temperature-ceiling', RAW_TEMP_CEILING_C],
    ['burned-temperature-ceiling', BURN_ABSOLUTE_CEILING_TEMP_C],
    ['defect-score-min', DEFECT_SCORE_MIN],
    ['defect-score-max', DEFECT_SCORE_MAX],
    ['perfect-score-min', PERFECT_SCORE_MIN],
    ['perfect-score-max', PERFECT_SCORE_MAX],
  ])('%s = %s', (property, simulatorValue) => {
    expect(readBackendDefault(property)).toBe(simulatorValue);
  });

  // La carga enviada es la temperatura del aire, que nunca baja del ambiente:
  // el mínimo del servidor solo tiene que quedar por debajo.
  test('charge-temperature-min no rechaza ninguna temperatura del aire posible', () => {
    expect(readBackendDefault('charge-temperature-min')).toBeLessThanOrEqual(MIN_AIR_TEMP_C);
  });
});

describe('escenarios reales compartidos con el backend', () => {
  const scenarios = JSON.parse(
    fs.readFileSync(path.join(BACKEND_DIR, 'src/test/resources/roast-session-scenarios.json'), 'utf8')
  );

  test('incluye desarrollo interrumpido, quemado por tiempo, horneado y cargas a 100, 264 y 300 °C', () => {
    const names = scenarios.map((scenario) => scenario.name);
    expect(names).toEqual(expect.arrayContaining([
      'pipeline-interrupted-development',
      'pipeline-charge-100-burned-by-time',
      'pipeline-baked',
      'pipeline-charge-100-perfect',
      'pipeline-charge-264-perfect',
      'pipeline-charge-300-perfect',
    ]));
  });

  // Si el evaluador cambia, este test obliga a regenerar el JSON; el backend
  // prueba que acepta cada uno de estos resultados.
  test.each(scenarios.flatMap((scenario) => LEVELS.map((level) => [scenario.name, level, scenario])))(
    '%s (%s) da el resultado y puntaje esperados',
    (name, level, scenario) => {
      const { result, score } = RoastQualityEvaluator.evaluate(scenario.sim, level);
      expect({ result, score }).toEqual(scenario.expected[level]);
    }
  );
});
