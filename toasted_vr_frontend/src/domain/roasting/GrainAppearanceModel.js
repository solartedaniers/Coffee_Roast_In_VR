import {
  MAILLARD_TEMP_START_C,
  MAILLARD_TEMP_END_C,
  FIRST_CRACK_TEMP_MIN_C,
  FIRST_CRACK_TEMP_MAX_C,
  SECOND_CRACK_TEMP_MAX_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
  BURN_THRESHOLD_TEMP_C,
} from './RoastConstants';

// Pigmento del grano en cada punto de temperatura — esto es física
// simulada (cómo se ve el café), no un color de diseño, por eso vive
// aquí como dato de dominio y no en los tokens CSS.
//
// Con el rango de first crack actualizado (196-205°C), FIRST_CRACK_TEMP_MIN_C
// (196) queda por debajo de MAILLARD_TEMP_END_C y de BURN_THRESHOLD_TEMP_C
// (ambos en 200) — el stop de "fin de Maillard" que existía antes (179°C)
// coincidiría con BURN_THRESHOLD_TEMP_C y rompería el orden ascendente que
// necesita interpolateColor(). Se elimina ese stop intermedio: el gradiente
// pasa de 5 a 4 puntos (verde → tostado claro → marrón → marrón oscuro),
// sin el matiz "fin de Maillard" que quedaba entre 179-189°C.
const GRAIN_COLOR_STOPS = [
  [20, [112, 130, 56]],
  [MAILLARD_TEMP_START_C, [230, 214, 144]],
  [FIRST_CRACK_TEMP_MIN_C, [123, 63, 0]],
  [FIRST_CRACK_TEMP_MAX_C, [59, 34, 25]],
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

  // El techo de "moderate" usa FIRST_CRACK_TEMP_MAX_C (205°C) en vez de
  // BURN_THRESHOLD_TEMP_C: con el rango de first crack actualizado, ambos
  // quedaban en 200°C y "moderate" nunca se alcanzaba (saltaba directo de
  // "faint" a "heavy"). 205°C es el mismo límite que ya separa la fase de
  // first crack de la fase oscura en getGrainStateName().
  static getSmokeLevel(temperature) {
    if (temperature < MAILLARD_TEMP_START_C) return 'none';
    if (temperature < MAILLARD_TEMP_END_C) return 'faint';
    if (temperature < FIRST_CRACK_TEMP_MAX_C) return 'moderate';
    return 'heavy';
  }

  static isBurntSmoke(temperature) {
    return temperature >= BURN_THRESHOLD_TEMP_C;
  }

  // firstCrackReached refleja el evento real de esta sesión (sorteado por
  // FirstCrackDetector en algún punto de 196-205°C), no solo la
  // temperatura: con MAILLARD_TEMP_END_C en 200°C, un tueste puede cruar
  // el first crack real (ej. a 197°C) antes de que la temperatura supere
  // el fin de Maillard — sin este dato, la etiqueta se quedaría diciendo
  // "Maillard" hasta los 200°C aunque el crack ya haya sonado.
  static getGrainStateName(temperature, firstCrackReached) {
    if (temperature <= MAILLARD_TEMP_START_C) return GRAIN_STATES.DRYING;
    if (!firstCrackReached) return GRAIN_STATES.MAILLARD;
    if (temperature <= FIRST_CRACK_TEMP_MAX_C) return GRAIN_STATES.FIRST_CRACK;
    if (temperature <= BURN_ABSOLUTE_CEILING_TEMP_C) return GRAIN_STATES.DARK;
    if (temperature <= SECOND_CRACK_TEMP_MAX_C) return GRAIN_STATES.SECOND_CRACK;
    return GRAIN_STATES.BURNED;
  }
}
