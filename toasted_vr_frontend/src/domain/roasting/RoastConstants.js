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

// ---- Perfil del grano verde (aleatorio por sesión) ------------------------
// Estas variables no son decorativas: alimentan directo la transferencia de
// calor (RoastAirModel/BeanHeatTransferCalculator) y los umbrales de crack,
// para que un grano más denso o más húmedo se comporte distinto en la curva,
// igual que en un tueste real. Se sortean una vez por sesión, mismo patrón
// que FirstCrackDetector.pickThresholdTemperature().

export const BEAN_DENSITY_MIN = 0.65;
export const BEAN_DENSITY_MAX = 0.85;
export const BEAN_DENSITY_REFERENCE = 0.75;

export const BEAN_MOISTURE_MIN_PCT = 8;
export const BEAN_MOISTURE_MAX_PCT = 12;
export const BEAN_MOISTURE_REFERENCE_PCT = 10;

export const BEAN_MESH_SIZE_MIN = 14;
export const BEAN_MESH_SIZE_MAX = 18;

// Temperatura real del grano al momento de cargarlo — antes siempre
// exactamente AMBIENT_TEMP_C (20°C fijo); ahora varía por sesión como el
// resto del perfil del grano, reflejando que la bodega/ambiente donde
// esperaba el lote no es siempre igual.
export const BEAN_ENTRY_TEMP_MIN_C = 15;
export const BEAN_ENTRY_TEMP_MAX_C = 25;

// °C que se desplaza el umbral de first/second crack por cada unidad de
// densidad por encima/debajo de BEAN_DENSITY_REFERENCE — un grano más denso
// tarda más en agrietarse.
export const CRACK_DENSITY_SHIFT_C_PER_DENSITY_UNIT = 40;

// ---- Lote / masa térmica ----------------------------------------

export const BATCH_WEIGHT_MIN_KG = 5;
export const BATCH_WEIGHT_MAX_KG = 6;
export const REFERENCE_BATCH_WEIGHT_KG = 5;

// ---- Límites de temperatura --------------------------------------

export const AMBIENT_TEMP_C = 20;
export const MAX_SAFE_TEMP_C = 250;

// Rango del slider de precalentado: no es un tope duro de calidad, solo el
// límite mecánico del control. 180-200°C es el rango "recomendado" para la
// carga del grano (ver más abajo); el usuario puede elegir fuera de él, a
// su propio riesgo de puntaje.
export const CHARGE_TEMP_MIN_C = 150;
export const CHARGE_TEMP_MAX_C = 220;
export const CHARGE_TEMP_DEFAULT_C = 190;

export const CHARGE_TEMP_IDEAL_MIN_C = 180;
export const CHARGE_TEMP_IDEAL_MAX_C = 200;
export const CHARGE_TEMP_PENALTY_WEIGHT = 1.5;

export const TARGET_TEMP_MIN_C = 150;
export const TARGET_TEMP_MAX_C = 230;
export const TARGET_TEMP_DEFAULT_C = 200;

// ---- Quemador / potencia de llama --------------------------------

export const FLAME_POWER_MIN_PCT = 0;
export const FLAME_POWER_MAX_PCT = 100;
export const FLAME_POWER_DEFAULT_PCT = 30;

// ---- Modelo de aire/tambor (ET) -----------------------------------------
// Modelo de dos cuerpos: el aire/tambor persigue un objetivo que depende de
// la potencia del quemador, y el grano persigue al aire (ver más abajo). Es
// el mismo tipo de curva de primer orden que ya usaba el modelo anterior
// (FLAME_RESPONSE_LAG_PER_SEC), solo que ahora el aire es una serie propia
// en vez de mezclarse directo con la temperatura del grano.

// Temperatura de equilibrio del aire si el quemador se sostuviera al 100%
// de potencia indefinidamente. Bajado de 800 a 750 (junto con
// HEAT_TRANSFER_COEFF_PER_SEC más abajo): con el aire respondiendo rápido
// (~15-20s), un techo tan alto hacía que el aire llegara casi de inmediato
// a ~254°C a potencia "eficiente" (30%) y se quedara ahí fijo el resto del
// tueste, exponiendo al grano a un salto térmico enorme desde el primer
// segundo de carga — la curva subía mucho más agresivo de lo creíble. Con
// 750 el pico de subida baja de ~35°C/min a ~30°C/min (validado con
// simulación numérica, no solo el test). Sigue por encima del rango de
// first crack, así el grano nunca alcanza del todo al aire.
export const MIN_AIR_TEMP_C = AMBIENT_TEMP_C;
export const MAX_AIR_TEMP_C = 750;

// El aire tiene poca masa térmica frente al grano, así que debe responder
// rápido a un cambio de potencia — 0.18 da un asentamiento de ~15s al 95%
// y ~23s al 99%, dentro de los 10-20s que pide el documento físico. La
// lentitud real de un tueste (8-10 min a first crack) no debe salir de
// aquí: sale de HEAT_TRANSFER_COEFF_PER_SEC más abajo, que representa la
// masa térmica del grano, mucho mayor que la del aire.
export const AIR_RESPONSE_RATE_PER_SEC = 0.18;

// Velocidad de respuesta del aire SOLO durante el precalentamiento — mucho
// más lenta que AIR_RESPONSE_RATE_PER_SEC de arriba (que sigue igual para
// carga/tueste, sin tocar). Calibrado para que a potencia "eficiente"
// (30%) y temperatura de carga por defecto, el precalentamiento tenga una
// duración SIMULADA realista de 8-10 min, en vez de los 8-60s que daba
// antes al compartir la misma tasa rápida del tueste.
export const PREHEAT_AIR_RESPONSE_RATE_PER_SEC = 0.0028;

// Ritmo del reloj REAL solo durante el precalentamiento: comprime esos
// 8-10 min simulados a ~2 min reales de espera para el usuario — el
// tiempo mostrado en pantalla sigue siendo el tiempo simulado real del
// modelo (no es un reloj cosmético desconectado de la física), solo que
// cada segundo simulado toma menos tiempo real en llegar.
export const PREHEAT_TICK_INTERVAL_MS = 225;

// ---- Transferencia de calor aire → grano ---------------------------------

// Coeficiente de conducción: cuánto del diferencial (aire - grano) se
// transfiere al grano por segundo. Se escala por masa térmica (lotes más
// grandes calientan más lento) y se divide por la densidad del grano
// (grano más denso absorbe más lento) y por el "calor específico efectivo"
// (grano más húmedo necesita más energía para subir un grado). Calibrado
// (junto con AIR_RESPONSE_RATE_PER_SEC y MAX_AIR_TEMP_C de arriba) para
// que un lote de referencia (5 kg, grano de densidad/humedad promedio) a
// potencia "eficiente" (30%) llegue al first crack en 8-12 min — ver
// RoastThermalModel.test.js, que es la herramienta usada para calibrar
// este valor. La ventana se amplió de 8-10 a 8-12 min a propósito: es el
// costo de bajar el pico de subida a algo más creíble (ver MAX_AIR_TEMP_C).
export const HEAT_TRANSFER_COEFF_PER_SEC = 0.0019;
export const SPECIFIC_HEAT_MOISTURE_FACTOR = 0.02;

// ---- Evaporación de humedad -----------------------------------------------

// Por debajo de este umbral casi no hay pérdida de agua libre relevante
// (el grano todavía está en fase de secado sin generar el efecto de
// enfriamiento evaporativo notorio de la curva real).
export const EVAP_THRESHOLD_TEMP_C = 120;
export const EVAP_RATE_CONSTANT_PER_SEC = 0.00025;
// Humedad residual mínima: el grano nunca queda completamente seco durante
// el tueste, así que la pérdida se corta cerca de este piso.
export const MOISTURE_FLOOR_PCT = 1.5;
// °C de enfriamiento por cada punto porcentual de humedad perdido por
// segundo — convierte la tasa de pérdida de agua en el término Q_evap de
// la ecuación 3.2 del modelo.
export const EVAP_COOLING_FACTOR_C_PER_PCT = 3.0;

// ---- Reacción exotérmica post first crack --------------------------------

// Calor propio que libera el grano una vez pasado el first crack —
// mantiene la curva subiendo aunque baje la potencia, como en un tueste
// real.
export const EXO_HEAT_RATE_C_PER_SEC = 0.03;

// ---- Carga del café (caída al cargar, ya no fija) --------------------

// °C/s de pérdida pico por cada grado que el aire estaba sobre la
// temperatura ambiente al momento de la carga — calibrado para reproducir
// ~0.9°C/s en las condiciones de referencia (carga a 190°C, ver
// ChargeDipCalculator.js): 0.9 / (190 - 20).
export const CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C = 0.00529;

// Duración base de la caída, a la potencia de referencia
// (FLAME_POWER_DEFAULT_PCT); se escala según la potencia real en cada
// instante — más potencia, recuperación más rápida y viceversa. Ya no es
// una duración fija: ver ChargeDipCalculator.computeEffectiveDurationSeconds,
// que también decide cuándo la fase pasa de carga a tueste, para que ese
// cambio de fase y el fin real de la caída siempre coincidan.
export const CHARGE_DIP_DURATION_BASE_SEC = 45;

// Piso de potencia para el cálculo de duración — evita dividir por cero
// (o una duración absurdamente larga) si la potencia está en 0%.
export const CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT = 5;

// Piso real del grano al momento de cargar (dónde "toca fondo" antes de
// empezar a subir) — promedio ponderado entre la temperatura del aire al
// cargar y la temperatura de entrada del grano, con el aire como variable
// principal: un ambiente caliente amortigua el choque térmico del grano
// frío (el grano "no alcanza a sentir" lo frío que estaba). Con
// AIR_WEIGHT=0.5, cargar a 200°C da un piso de ~110°C en vez de quedarse
// cerca de la temperatura de entrada (~20°C) como antes.
//
// ADVERTENCIA: con este peso, el first crack ya NO cae siempre dentro de
// la ventana 8-12 min que valida RoastThermalModel.test.js para el caso
// de referencia (sin carga) — a partir de ~180-200°C de carga, el tueste
// se adelanta notablemente (ver simulación de la sesión que introdujo
// este cambio). Decisión consciente: se priorizó el realismo físico del
// piso sobre esa ventana de tiempo. RoastThermalModel.test.js no lo
// detecta porque simula un tueste sin fase de carga — sigue pasando.
export const CHARGE_DIP_FLOOR_AIR_WEIGHT = 0.5;

// ---- Ritmo del reloj real por fase --------------------------------

// Antes la carga corría a 2.5x más lento que el tueste (deliberado, para
// que no se sintiera apurada), pero eso hacía que la curva se dibujara a
// saltos (un punto nuevo cada 2.5s reales) en vez de fluida. Se probaron
// 1000ms y 500ms; a 500ms se sentía demasiado rápida/apurada, así que
// queda igual al ritmo del tueste (1:1) — ver RoastPacingProfile.js.
export const CHARGE_DIP_TICK_INTERVAL_MS = 1000;
export const ROASTING_TICK_INTERVAL_MS = 1000;

// ---- Incremento de temperatura (Increm °T) ------------------------

export const RATE_OF_RISE_WINDOW_SEC = 30;

// ---- First crack ---------------------------------------------------

export const FIRST_CRACK_TEMP_MIN_C = 189;
export const FIRST_CRACK_TEMP_MAX_C = 198;

// ---- Second crack ---------------------------------------------------

export const SECOND_CRACK_TEMP_MIN_C = 224;
export const SECOND_CRACK_TEMP_MAX_C = 228;

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

// Condición combinada (sección 5 del modelo): un incremento de temperatura
// demasiado agresivo mientras casi no queda humedad para amortiguarlo
// también quema el grano, aunque no se cumpla la regla de tiempo.
export const ROR_SCORCH_THRESHOLD_C_PER_MIN = 35;
export const MOISTURE_SCORCH_THRESHOLD_PCT = 2;

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

// ---- Ventana de tiempo de la gráfica ---------------------------------

// La gráfica muestra una ventana móvil de los últimos N segundos en
// vez de estirar todo el tueste (9-15 min) en el mismo ancho: así los
// picos y valles reales (caída al cargar el café, reacción a cambios
// de potencia) se ven con detalle en vez de aplanarse. Igual a
// CHART_HORIZONTAL_TICK_COUNT minutos exactos para que la ventana
// llene todo el ancho del eje sin dejar un margen vacío al final —
// 15 min para un ritmo de avance pausado (~0.84 px/s), cercano al que
// había antes de que existiera esta ventana.
export const CHART_HORIZONTAL_TICK_COUNT = 15;
export const CHART_TIME_WINDOW_SECONDS = CHART_HORIZONTAL_TICK_COUNT * 60;

// Suavizado visual de caídas bruscas (ej. al cargar el grano en el tambor
// caliente): ninguna caída entre puntos consecutivos de la línea dibujada
// supera este valor — el exceso se reparte hacia adelante. Solo afecta la
// línea de la gráfica, nunca las muestras crudas que alimentan el panel de
// datos ni el puntaje. Este valor (19) se calibró cuando CHARGE_DIP corría
// a 2.5s/muestra (caso de referencia: carga a 190°C, salto de 170°C → 9
// muestras → 22.5s reales) — ahora que CHARGE_DIP_TICK_INTERVAL_MS es 1s
// igual que el tueste, la misma caída se resuelve en ~9s reales (9
// muestras × 1s), 2.5x más rápido que antes. Pendiente confirmar tras
// probar si esa duración sigue viéndose bien o si hay que bajar este
// valor para volver a estirar la caída a ~20-25s reales.
export const CHART_SMOOTHING_MAX_DROP_C_PER_SAMPLE = 19;

// Línea de referencia fija (piso) de la gráfica — no depende de ningún
// slider ni del rango recomendado de carga (180-200°C, ver
// CHARGE_TEMP_IDEAL_MIN_C/MAX_C): es solo una marca visual de referencia
// baja en el eje.
export const CHART_FLOOR_REFERENCE_TEMP_C = 80;

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
// Con el modelo térmico realista, un lote de 5-6 kg a potencia "eficiente"
// llega al first crack en 8-10 min y un tueste completo típico dura
// 12-15 min — el punto medio de esa ventana es el nuevo ideal (antes 9,
// calibrado para el modelo simplificado anterior).
export const IDEAL_TOTAL_ROAST_MINUTES = 13;
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
