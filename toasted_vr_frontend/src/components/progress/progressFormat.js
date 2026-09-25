import RoastMetrics from '../../domain/roasting/RoastMetrics';

// Formatos compartidos por la lista y el detalle del historial.
export const RESULT_PILL_CLASSES = Object.freeze({
  PERFECT: 'result-pill-perfect',
  RAW: 'result-pill-raw',
  BURNED: 'result-pill-burned',
  BAKED: 'result-pill-baked',
});

// Las sesiones anteriores a RF009 no tienen nivel guardado.
export const formatLevel = (level, texts) => texts.levels[level] || texts.noValue;

export const formatTemperature = (value, texts) => (value == null ? texts.noValue : `${value.toFixed(1)} °C`);

export const formatMinutes = (seconds, texts) =>
  seconds == null ? texts.noValue : `${RoastMetrics.formatDecimalMinutes(seconds)} ${texts.minutesUnit}`;

// El DTR llega como fracción (0.192) y se muestra como porcentaje (19.2 %).
export const formatRatio = (ratio, texts) => (ratio == null ? texts.noValue : `${(ratio * 100).toFixed(1)} %`);
