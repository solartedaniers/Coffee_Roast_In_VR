import {
  MAILLARD_TEMP_START_C,
  MAILLARD_TEMP_END_C,
  FIRST_CRACK_TEMP_MAX_C,
  SECOND_CRACK_TEMP_MAX_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  BURN_THRESHOLD_TEMP_C,
} from './RoastConstants';

// Pigmento del grano en cada punto de temperatura — esto es física
// simulada (cómo se ve el café), no un color de diseño, por eso vive
// aquí como dato de dominio y no en los tokens CSS.
const GRAIN_COLOR_STOPS = [
  [20, [112, 130, 56]],
  [130, [230, 214, 144]],
  [179, [179, 139, 109]],
  [189, [123, 63, 0]],
  [198, [59, 34, 25]],
  [BURN_THRESHOLD_TEMP_C, [26, 17, 16]],
];

const GRAIN_STATES = Object.freeze({
  DRYING: 'DRYING',
  MAILLARD: 'MAILLARD',
  FIRST_CRACK: 'FIRST_CRACK',
  DARK: 'DARK',
  SECOND_CRACK: 'SECOND_CRACK',
  BURNED: 'BURNED',
});

// ================================================================
// GrainAppearanceModel
// Responsabilidad única: traducir una temperatura del grano a cómo
// debe verse en pantalla (color, humo, nombre del estado).
// ================================================================
export default class GrainAppearanceModel {
  static interpolateColor(temperature) {
    const stops = GRAIN_COLOR_STOPS;
    if (temperature <= stops[0][0]) {
      const [r, g, b] = stops[0][1];
      return `rgb(${r},${g},${b})`;
    }
    const last = stops[stops.length - 1];
    if (temperature >= last[0]) {
      const [r, g, b] = last[1];
      return `rgb(${r},${g},${b})`;
    }
    for (let i = 0; i < stops.length - 1; i++) {
      const [t0, c0] = stops[i];
      const [t1, c1] = stops[i + 1];
      if (temperature >= t0 && temperature <= t1) {
        const ratio = (temperature - t0) / (t1 - t0);
        const r = Math.round(c0[0] + ratio * (c1[0] - c0[0]));
        const g = Math.round(c0[1] + ratio * (c1[1] - c0[1]));
        const b = Math.round(c0[2] + ratio * (c1[2] - c0[2]));
        return `rgb(${r},${g},${b})`;
      }
    }
    const [r, g, b] = last[1];
    return `rgb(${r},${g},${b})`;
  }

  static getSmokeLevel(temperature) {
    if (temperature < MAILLARD_TEMP_START_C) return 'none';
    if (temperature < MAILLARD_TEMP_END_C) return 'faint';
    if (temperature < BURN_THRESHOLD_TEMP_C) return 'moderate';
    return 'heavy';
  }

  static isBurntSmoke(temperature) {
    return temperature >= BURN_THRESHOLD_TEMP_C;
  }

  static getGrainStateName(temperature) {
    if (temperature <= MAILLARD_TEMP_START_C) return GRAIN_STATES.DRYING;
    if (temperature <= MAILLARD_TEMP_END_C) return GRAIN_STATES.MAILLARD;
    if (temperature <= FIRST_CRACK_TEMP_MAX_C) return GRAIN_STATES.FIRST_CRACK;
    if (temperature <= BURN_ABSOLUTE_CEILING_TEMP_C) return GRAIN_STATES.DARK;
    if (temperature <= SECOND_CRACK_TEMP_MAX_C) return GRAIN_STATES.SECOND_CRACK;
    return GRAIN_STATES.BURNED;
  }
}
