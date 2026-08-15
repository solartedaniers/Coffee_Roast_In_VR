// ================================================================
// CONSTANTES DE DOMINIO DEL TUESTE
// Solo parámetros físicos/de simulación — nada de textos ni colores
// de interfaz.
// ================================================================

export const PHASES = Object.freeze({
  IDLE: 'IDLE',
  PREHEAT: 'PREHEAT',
  READY: 'READY',
  CHARGE_DIP: 'CHARGE_DIP',
  ROASTING: 'ROASTING',
  FINISHED: 'FINISHED',
});

export const OPERATION_MODES = Object.freeze({
  MANUAL: 'MANUAL',
  AUTO: 'AUTO',
});

export const TEMPERATURE_UNITS = Object.freeze({
  CELSIUS: 'C',
  FAHRENHEIT: 'F',
});

export const RESULTS = Object.freeze({
  PERFECT: 'PERFECT',
  RAW: 'RAW',
  BURNED: 'BURNED',
  BAKED: 'BAKED',
});

// ---- Lote / masa térmica ----------------------------------------

export const BATCH_WEIGHT_KG = 5;
export const REFERENCE_BATCH_WEIGHT_KG = 5;
export const THERMAL_MASS_FACTOR = REFERENCE_BATCH_WEIGHT_KG / BATCH_WEIGHT_KG;

// ---- Límites de temperatura --------------------------------------

export const AMBIENT_TEMP_C = 20;
export const MAX_SAFE_TEMP_C = 250;

export const CHARGE_TEMP_MIN_C = 60;
export const CHARGE_TEMP_MAX_C = 150;
export const CHARGE_TEMP_DEFAULT_C = 80;

export const TARGET_TEMP_MIN_C = 150;
export const TARGET_TEMP_MAX_C = 230;
export const TARGET_TEMP_DEFAULT_C = 200;

// ---- Quemador / potencia de llama --------------------------------

export const FLAME_POWER_MIN_PCT = 0;
export const FLAME_POWER_MAX_PCT = 100;
export const FLAME_POWER_DEFAULT_PCT = 30;

// °C/s que puede sumar el quemador al 100% de potencia, desde un
// tambor frío, antes de pérdidas.
export const BURNER_MAX_HEAT_RATE_C_PER_SEC = 2.0;

// Temperatura del tambor en la que se estabilizaría el quemador si se
// mantuviera al 100% de potencia indefinidamente (lote de referencia).
// Calibrado para que el 30% de potencia — el valor "eficiente y
// estable" que se observa en tostadoras reales — sea suficiente para
// completar un tueste medio (llegar al rango de first crack en unos
// 9 minutos). El 100% sostenido apunta muy por encima de
// MAX_SAFE_TEMP_C: nunca se estabiliza de verdad, solo sigue subiendo
// hacia el límite de seguridad — es una condición de riesgo extremo
// que arrebata el grano en pocos minutos, no un valor de operación normal.
export const HEAT_LOSS_REFERENCE_EQUILIBRIUM_C = 800;

export const HEAT_LOSS_COEFF_PER_SEC =
  BURNER_MAX_HEAT_RATE_C_PER_SEC / (HEAT_LOSS_REFERENCE_EQUILIBRIUM_C - AMBIENT_TEMP_C);

// La válvula de gas trabaja en pulsos cortos en vez de responder al
// instante — la potencia de llama se acerca a su valor objetivo
// gradualmente en lugar de saltar directo a él.
export const FLAME_RESPONSE_LAG_PER_SEC = 0.05 * THERMAL_MASS_FACTOR;

// ---- Carga del café --------------------------------------------

export const CHARGE_DIP_DURATION_SEC = 45;
export const CHARGE_DIP_PEAK_LOSS_C_PER_SEC = 0.9;

// ---- Incremento de temperatura (Increm °T) ------------------------

export const RATE_OF_RISE_WINDOW_SEC = 30;

// ---- First crack ---------------------------------------------------

export const FIRST_CRACK_TEMP_MIN_C = 189;
export const FIRST_CRACK_TEMP_MAX_C = 198;

// ---- Riesgo de quemado ------------------------------------------------

// Zona de riesgo: a partir de aquí se cuenta el tiempo consecutivo
// expuesto (regla de tiempo excesivo, ver BurnRiskMonitor).
export const BURN_THRESHOLD_TEMP_C = 200;
export const BURN_CONSECUTIVE_LIMIT_SEC = 15;

// Techo absoluto de temperatura final: superarlo quema el lote de
// inmediato al momento de evaluar, sin importar cuánto tiempo estuvo
// ahí. Queda por encima de BURN_THRESHOLD_TEMP_C a propósito, para
// permitir tuestes medios y medios-altos que pasan por los 200°C
// brevemente sin penalizarlos injustamente.
export const BURN_ABSOLUTE_CEILING_TEMP_C = 213;

// ---- Estancamiento en Maillard (defecto "horneado") --------------------

export const MAILLARD_TEMP_START_C = 131;
export const MAILLARD_TEMP_END_C = 179;
export const MAILLARD_STAGNATION_LIMIT_SEC = 30;

// ---- Controlador del modo automático -----------------------------------

// Controlador proporcional-derivativo: potencia = base
//   + ganancia * (Temp Ctrl - actual)
//   - amortiguación * (Increm °T actual).
// El término derivativo hace que el modo automático empiece a soltar
// potencia en cuanto detecta que el café ya está subiendo rápido, sin
// esperar a que la temperatura realmente cruce el objetivo — así evita
// pasarse de largo por la inercia del quemador. El techo de potencia
// mantiene al modo automático dentro del mismo rango "eficiente y
// estable" (30%) que se recomienda en modo manual, en vez de tirar
// siempre al máximo.
export const AUTO_MODE_BASE_POWER_PCT = 10;
export const AUTO_MODE_GAIN_PCT_PER_C = 8;
export const AUTO_MODE_DAMPING_PCT_PER_C_PER_MIN = 1.2;
export const AUTO_MODE_MAX_POWER_PCT = 30;

// ---- Eje vertical de la gráfica ------------------------------------------

export const CHART_VERTICAL_STEP_C = 33;
export const CHART_VERTICAL_TICK_COUNT = 6;

// ---- Evaluación: Crudo --------------------------------------------------

export const RAW_TEMP_CEILING_C = FIRST_CRACK_TEMP_MIN_C - 10;
export const RAW_SCORE_MAX = 38;

// ---- Evaluación: Quemado --------------------------------------------------

export const BURN_SCORE_BASE = 18;
export const BURN_SCORE_PENALTY_PER_EXTRA_SEC = 0.4;

// ---- Evaluación: Horneado (estancamiento en Maillard) ----------------------

// El puntaje ya no es fijo: arranca en BAKED_SCORE_AT_THRESHOLD justo
// al momento en que el estancamiento dispara el defecto (30 s) y baja
// de forma proporcional por cada segundo adicional que el grano se
// mantuvo estancado, hasta un piso de BAKED_SCORE_FLOOR.
export const BAKED_SCORE_AT_THRESHOLD = 45;
export const BAKED_SCORE_FLOOR = 10;
export const BAKED_SCORE_PENALTY_PER_EXTRA_SEC = 0.5;

// ---- Rango común de puntajes defectuosos (Crudo/Quemado/Horneado) ----------

export const DEFECT_SCORE_MIN = 5;
export const DEFECT_SCORE_MAX = 50;

// ---- Evaluación: Perfecto --------------------------------------------------

export const IDEAL_FINAL_TEMP_C = (FIRST_CRACK_TEMP_MIN_C + FIRST_CRACK_TEMP_MAX_C) / 2;
export const IDEAL_TOTAL_ROAST_MINUTES = 9;
export const PERFECT_TEMP_PENALTY_WEIGHT = 2.5;
export const PERFECT_TIME_PENALTY_WEIGHT = 4;
export const PERFECT_SCORE_MIN = 0;
export const PERFECT_SCORE_MAX = 100;

// Ratio de Desarrollo (DTR — Development Time Ratio): proporción del
// tueste transcurrida después del first crack, estándar de la
// industria del tueste. Dentro del rango ideal no hay penalización;
// fuera de él, se penaliza proporcionalmente a cuánto se desvía.
export const DTR_IDEAL_MIN_RATIO = 0.15;
export const DTR_IDEAL_MAX_RATIO = 0.25;
export const DTR_PENALTY_PER_RATIO_POINT = 100;

export const CRACK_SOUND_PATH = `${process.env.PUBLIC_URL}/assets/sounds/crack.mp3`;

// ---- Ajustes del equipo (persisten entre tuestes, viven en el menú
// de configuración general y no en el estado de cada simulación) --------

// Un único offset de sensor combinado: el modelo solo lleva una serie
// de temperatura física, así que "calibrar las termocuplas del café y
// del tambor" se representa como una sola corrección aplicada a cada
// lectura que se muestra en pantalla.
export const SENSOR_CALIBRATION_MIN_C = -5;
export const SENSOR_CALIBRATION_MAX_C = 5;
export const SENSOR_CALIBRATION_DEFAULT_C = 0;

// Límite máximo de seguridad del equipo — independiente de
// BURN_THRESHOLD_TEMP_C (la regla de "café quemado" para la
// calificación, que nunca cambia). Superar este límite dispara una
// alarma de protección del equipo, no una alarma de calidad.
export const ALARM_LIMIT_MIN_C = BURN_THRESHOLD_TEMP_C;
export const ALARM_LIMIT_MAX_C = MAX_SAFE_TEMP_C;
export const ALARM_LIMIT_DEFAULT_C = 230;

export const CHART_STEP_OPTIONS_C = Object.freeze([20, 33, 50]);
