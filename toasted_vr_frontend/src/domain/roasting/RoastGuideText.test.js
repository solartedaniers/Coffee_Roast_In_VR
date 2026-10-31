import texts from '../../locals/es.json';
import {
  GUIDE_DRYING_FLAME_PCT,
  GUIDE_MAILLARD_FLAME_PCT,
  GUIDE_DEVELOPMENT_FLAME_PCT,
  GUIDE_STEP_DOWN_TEMP_C,
  GUIDE_FAST_RISE_C_PER_MIN,
  GUIDE_SLOW_RISE_C_PER_MIN,
} from './RoastConstants';

// La guía es texto fijo en es.json: si cambia una constante de la guía, el texto tiene que cambiar con ella.
const guideTexts = Object.entries(texts.simulation.guidance)
  .filter(([mode]) => mode !== 'title')
  .map(([mode, phases]) => [mode, phases.ROASTING]);

describe.each(guideTexts)('guía %s del tueste', (mode, text) => {
  test.each([
    [`${GUIDE_DRYING_FLAME_PCT} %`],
    [`${GUIDE_MAILLARD_FLAME_PCT} %`],
    [`${GUIDE_DEVELOPMENT_FLAME_PCT} %`],
    [`${GUIDE_STEP_DOWN_TEMP_C}°C`],
    [`${GUIDE_FAST_RISE_C_PER_MIN}°C por minuto`],
    [`${GUIDE_SLOW_RISE_C_PER_MIN}°C por minuto`],
  ])('menciona %s', (fragment) => {
    expect(text).toContain(fragment);
  });
});
