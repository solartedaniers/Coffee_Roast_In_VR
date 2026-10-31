import fs from 'fs';
import path from 'path';
import texts from '../locals/es.json';
import {
  GUIDE_DRYING_FLAME_PCT,
  GUIDE_MAILLARD_FLAME_PCT,
  GUIDE_DEVELOPMENT_FLAME_PCT,
  GUIDE_STEP_DOWN_TEMP_C,
  GUIDE_FAST_RISE_C_PER_MIN,
  GUIDE_SLOW_RISE_C_PER_MIN,
  DTR_IDEAL_MIN_RATIO,
  DTR_IDEAL_MAX_RATIO,
} from '../domain/roasting/RoastConstants';

// El asistente de voz aconseja el gas con la misma guía que ve el jugador en la web.
const AI_GUIDE_FILE = path.resolve(__dirname, '../../../IA/config/roast_guide.json');
const aiGuide = JSON.parse(fs.readFileSync(AI_GUIDE_FILE, 'utf8')).text;
const toPercent = (ratio) => Math.round(ratio * 100);

test('la guía del asistente de voz es el mismo texto de es.json', () => {
  expect(aiGuide).toBe(texts.simulation.guidance.complete.ROASTING);
});

test.each([
  [`${GUIDE_DRYING_FLAME_PCT} %`],
  [`${GUIDE_MAILLARD_FLAME_PCT} %`],
  [`${GUIDE_DEVELOPMENT_FLAME_PCT} %`],
  [`${GUIDE_STEP_DOWN_TEMP_C}°C`],
  [`${GUIDE_FAST_RISE_C_PER_MIN}°C por minuto`],
  [`${GUIDE_SLOW_RISE_C_PER_MIN}°C por minuto`],
  [`${toPercent(DTR_IDEAL_MIN_RATIO)}% y ${toPercent(DTR_IDEAL_MAX_RATIO)}%`],
])('la guía del asistente de voz usa %s como RoastConstants.js', (fragment) => {
  expect(aiGuide).toContain(fragment);
});
