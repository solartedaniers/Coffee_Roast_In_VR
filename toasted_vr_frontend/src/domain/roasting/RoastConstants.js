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
// El experto arranca cada tueste con la llama al 50 %.
export const FLAME_POWER_DEFAULT_PCT = 50;

// ---- Modelo de aire/tambor (ET) -----------------------------------------
// Modelo de dos cuerpos: el aire/tambor persigue un objetivo que depende de
// la potencia del quemador, y el grano persigue al aire (ver más abajo). Es
// el mismo tipo de curva de primer orden que ya usaba el modelo anterior
// (FLAME_RESPONSE_LAG_PER_SEC), solo que ahora el aire es una serie propia
// en vez de mezclarse directo con la temperatura del grano.

// Temperatura de equilibrio del aire con el quemador al 100 %. El objetivo
// del aire crece como (gas/100)^AIR_TARGET_GAS_EXPONENT: con un exponente
// tan bajo la curva es casi plana (20 % → ~205 °C, 50 % → ~224 °C), que es
// lo que piden los tuestes del experto (calibration/expert-roasts.csv) —
// con 50 / 40 / 20 % su sonda sigue subiendo después del primer crack sin
// pasarse. Calibrado junto con HEAT_TRANSFER_COEFF_PER_SEC,
// CHARGE_DIP_FLOOR_AIR_WEIGHT, EXO_HEAT_RATE_C_PER_SEC y
// PROBE_RESPONSE_TIME_CONSTANT_SEC, con el experto como lote de 6 kg. El
// experto no pasó de 50 %: lo que hay por encima es extrapolación.
export const MIN_AIR_TEMP_C = AMBIENT_TEMP_C;
export const MAX_AIR_TEMP_C = 239;
export const AIR_TARGET_GAS_EXPONENT = 0.105;

// El aire tiene poca masa térmica frente al grano, así que debe responder
// rápido a un cambio de potencia — 0.18 da un asentamiento de ~15s al 95%
// y ~23s al 99%, dentro de los 10-20s que pide el documento físico. La
// lentitud real de un tueste (8-10 min a first crack) no debe salir de
// aquí: sale de HEAT_TRANSFER_COEFF_PER_SEC más abajo, que representa la
// masa térmica del grano, mucho mayor que la del aire.
export const AIR_RESPONSE_RATE_PER_SEC = 0.18;

// Velocidad de respuesta del aire SOLO durante el precalentamiento — mucho
// más lenta que AIR_RESPONSE_RATE_PER_SEC de arriba (que sigue igual para
// carga/tueste, sin tocar). Calibrado para que con la llama de arranque
// (FLAME_POWER_DEFAULT_PCT) y la temperatura de carga por defecto, el
// precalentamiento dure 8-10 min simulados en lotes de 5 a 6 kg — ver
// RoastPacingProfile.test.js.
export const PREHEAT_AIR_RESPONSE_RATE_PER_SEC = 0.0037;

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
// con MAX_AIR_TEMP_C para que, siguiendo la guía, el grano de referencia
// llegue al primer crack en 8-12 min en lotes de 5 a 6 kg — ver
// ExpertCurveCalibration.test.js.
export const HEAT_TRANSFER_COEFF_PER_SEC = 0.0038;
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
// real. Con el aire nuevo basta un aporte pequeño: más alto, la estrategia
// del experto pasa de 212 °C al descargar con DTR del 20 %.
export const EXO_HEAT_RATE_C_PER_SEC = 0.002;

// ---- Carga del café (caída al cargar, ya no fija) --------------------

// °C/s de pérdida pico por cada grado que el aire estaba sobre la
// temperatura ambiente al momento de la carga — calibrado para reproducir
// ~0.9°C/s en las condiciones de referencia (carga a 190°C, ver
// ChargeDipCalculator.js): 0.9 / (190 - 20).
export const CHARGE_DIP_LOSS_COEFF_PER_DEGREE_C = 0.00529;

// Duración base de la caída del aire, a la potencia de referencia
// (FLAME_POWER_DEFAULT_PCT); se escala según la potencia real en cada
// instante — más potencia, recuperación más rápida y viceversa. Ver
// ChargeDipCalculator.computeEffectiveDurationSeconds. El paso de carga a
// tueste ya no depende de esto: lo marca el punto de retorno de la sonda.
export const CHARGE_DIP_DURATION_BASE_SEC = 45;

// Piso de potencia para el cálculo de duración — evita dividir por cero
// (o una duración absurdamente larga) si la potencia está en 0%.
export const CHARGE_DIP_MIN_POWER_FOR_DURATION_PCT = 5;

// Temperatura con la que arranca el grano al cargar — promedio ponderado
// entre el aire al cargar y la temperatura de entrada del grano. Antes el
// aire pesaba 0.5 para que la lectura no se desplomara; ahora la sonda
// muestra el enfriamiento gradual, así que el grano entra a su propia
// temperatura (peso 0) y el punto de retorno del lote de 6 kg cae en
// 98-99 °C, como en los tuestes del experto.
export const CHARGE_DIP_FLOOR_AIR_WEIGHT = 0;

// ---- Sonda de temperatura ---------------------------------------------
// El termómetro de la tostadora es una sonda metida en la masa de grano:
// al cargar todavía marca el calor del tambor y se enfría poco a poco
// hasta igualarse con el grano (retraso de primer orden). La caída de los
// dos tuestes del experto (calibration/expert-roasts.csv) baja a lo sumo
// 2.2 °C/s: con el grano entrando a su temperatura, 79 s es lo mínimo que
// no baja más rápido que eso, y deja el punto de retorno del lote de 6 kg
// en 98-99 °C.
export const PROBE_RESPONSE_TIME_CONSTANT_SEC = 79;

// Grados que la sonda debe subir sobre su mínima desde la carga para
// marcar el punto de retorno: ahí termina la fase de carga. Mismo valor
// que turningPointMinRise de las alertas y la rúbrica de la IA.
export const TURNING_POINT_MIN_RISE_C = 0.5;

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

// Rango actualizado según Debona et al. 2022 / Bustos-Vanegas et al. 2025
// (antes 189-198°C).
export const FIRST_CRACK_TEMP_MIN_C = 196;
export const FIRST_CRACK_TEMP_MAX_C = 205;

// ---- Second crack ---------------------------------------------------

export const SECOND_CRACK_TEMP_MIN_C = 224;
export const SECOND_CRACK_TEMP_MAX_C = 228;

// ---- Riesgo de quemado ------------------------------------------------

// Zona de alta temperatura para los efectos visuales (humo y termómetro
// del horno) y mínimo de la alarma del equipo. Ya no califica: después
// del first crack, el tiempo sobre 200°C lo controla el DTR.
export const BURN_THRESHOLD_TEMP_C = 200;

// Techo del tueste medio: si el grano pasa de aquí en algún momento, el
// lote queda quemado. Fuente: manual de tostado p.10 (temperatura final
// más alta del tueste "medio alto", 200-220°C) y tabla 2 del artículo de
// Jama (finales de 220-221°C con Agtron 55-65). Coincide con el segundo
// crack más temprano posible (224°C - 4°C por densidad).
export const BURN_ABSOLUTE_CEILING_TEMP_C = 220;

// Desde aquí se avisa que el grano se acerca al techo. Parámetro de
// diseño del simulador.
export const BURN_WARNING_TEMP_C = 215;

// Más de estos segundos seguidos sobre el techo describen el quemado
// como severo. Parámetro de diseño del simulador.
export const BURN_SEVERE_SECONDS_ABOVE_CEILING = 15;

// Condición combinada (sección 5 del modelo): un incremento de temperatura
// demasiado agresivo mientras casi no queda humedad para amortiguarlo
// también quema el grano, aunque no se cumpla la regla de tiempo.
// Parámetro de diseño: con el aire calibrado al experto el Increm °T llega
// a ~17 °C/min siguiendo la guía y a ~20 °C/min con el gas al 100 %, así
// que 19 °C/min solo se alcanza forzando el gas con el grano ya seco.
export const ROR_SCORCH_THRESHOLD_C_PER_MIN = 19;
export const MOISTURE_SCORCH_THRESHOLD_PCT = 2;

// ---- Estancamiento en Maillard (defecto "horneado") --------------------

// Rango actualizado según Debona et al. 2022 / Bustos-Vanegas et al. 2025
// (antes 131-179°C). NOTA: 200°C queda por encima de FIRST_CRACK_TEMP_MIN_C
// (196°C) — un tueste puede cruar el first crack antes de que el modelo
// considere terminada la fase Maillard. Aceptado a propósito: afecta solo
// la etiqueta de fase mostrada (GrainAppearanceModel/RoastFeedbackPromptBuilder)
// en una franja de 4°C, no el puntaje ni la física.
export const MAILLARD_TEMP_START_C = 160;
export const MAILLARD_TEMP_END_C = 200;
export const MAILLARD_STAGNATION_LIMIT_SEC = 30;

// ---- Guía de gas en el tueste (texto ROASTING de es.json) -----------------

// La estrategia del experto: 50 % hasta GUIDE_STEP_DOWN_TEMP_C, 40 % hasta
// el primer crack y 20 % desde ahí. El jugador además mira el Increm °T:
// si sube más que GUIDE_FAST_RISE_C_PER_MIN baja a 40 % antes y lo deja
// ahí, y solo si en 40 % sube menos que GUIDE_SLOW_RISE_C_PER_MIN vuelve a
// 50 % hasta el crack (un tostador no mueve el gas a cada rato). Los dos
// umbrales de subida son parámetros de diseño, ajustados para que el
// primer crack caiga en 8-12 min en la mayoría de los granos.
export const GUIDE_DRYING_FLAME_PCT = FLAME_POWER_DEFAULT_PCT;
export const GUIDE_MAILLARD_FLAME_PCT = 40;
export const GUIDE_DEVELOPMENT_FLAME_PCT = 20;
export const GUIDE_STEP_DOWN_TEMP_C = 185;
export const GUIDE_FAST_RISE_C_PER_MIN = 16;
export const GUIDE_SLOW_RISE_C_PER_MIN = 10;

// ---- Controlador del modo automático -----------------------------------

// Controlador proporcional-derivativo: potencia = base
//   + ganancia * (Temp Ctrl - actual)
//   - amortiguación * (Increm °T actual).
// El término derivativo hace que el modo automático empiece a soltar
// potencia en cuanto detecta que el café ya está subiendo rápido, sin
// esperar a que la temperatura realmente cruce el objetivo — así evita
// pasarse de largo por la inercia del quemador. El techo y la base son los
// extremos de la guía del experto (50 % y 20 %): con 20 % el aire (~205 °C)
// queda sobre el inicio del rango del primer crack, así que en el objetivo
// la temperatura se sostiene en vez de caer.
export const AUTO_MODE_BASE_POWER_PCT = 20;
export const AUTO_MODE_GAIN_PCT_PER_C = 8;
export const AUTO_MODE_DAMPING_PCT_PER_C_PER_MIN = 1.2;
export const AUTO_MODE_MAX_POWER_PCT = 50;

// ---- Apariencia del grano ------------------------------------------------

// Hasta aquí el grano se ve "oscuro" y desde aquí "segundo crack". Es solo
// visual: se dejó en el valor del techo de quemado anterior para no cambiar
// cómo se ve el grano al mover la regla de quemado a 220°C.
export const GRAIN_DARK_STATE_MAX_TEMP_C = 217;

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

// La gráfica dibuja un punto cada tantos segundos simulados, para que la
// línea se vea fluida; el Incremento °C/min usa las muestras de cada
// segundo y la traza va cada ROAST_TRACE_SAMPLE_INTERVAL_SECONDS.
export const CHART_SAMPLE_INTERVAL_SECONDS = 2;

// Línea de referencia fija (piso) de la gráfica — no depende de ningún
// slider ni del rango recomendado de carga (180-200°C, ver
// CHARGE_TEMP_IDEAL_MIN_C/MAX_C): es solo una marca visual de referencia
// baja en el eje.
export const CHART_FLOOR_REFERENCE_TEMP_C = 80;

// ---- Evaluación: Crudo --------------------------------------------------

export const RAW_TEMP_CEILING_C = FIRST_CRACK_TEMP_MIN_C - 10;
export const RAW_SCORE_MAX = 38;

// ---- Evaluación: Quemado --------------------------------------------------

// Parte de BURN_SCORE_BASE y resta por cada segundo seguido sobre el
// techo de quemado. Parámetro de diseño del simulador.
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
// tueste transcurrida después del first crack. El rango ideal 18-22 % es
// el de la tesis para cafés de altura de Nariño (el mismo que usa el
// servicio IA) y es igual para todos los niveles. Cada nivel suma su
// tolerancia (KnowledgeLevelRules.dtrToleranceRatio) antes de penalizar;
// fuera de esa franja se penaliza proporcionalmente a cuánto se desvía.
export const DTR_IDEAL_MIN_RATIO = 0.18;
export const DTR_IDEAL_MAX_RATIO = 0.22;
export const DTR_PENALTY_PER_RATIO_POINT = 100;

// ---- Traza del tueste para la calificación de la IA (fase 5) ----
// Lo que decidió el jugador, muestreado cada pocos segundos desde la carga.
// Los máximos son los de app.roast-trace en application.yml
// (roastValidationContract.test.js); una traza más grande no se envía y la
// sesión queda calificada solo con las reglas.
export const ROAST_TRACE_VERSION = 1;
export const ROAST_TRACE_SAMPLE_INTERVAL_SECONDS = 5;
export const ROAST_TRACE_MAX_SAMPLES = 400;
export const ROAST_TRACE_MAX_GAS_CHANGES = 120;
export const ROAST_TRACE_MAX_EVENTS = 10;

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
