import { RATE_OF_RISE_WINDOW_SEC } from './RoastConstants';

// ================================================================
// RoastMetrics
// Responsabilidad única: derivar números listos para mostrar (Increm
// °T, formatos de tiempo) a partir del historial de temperatura.
// ================================================================
export default class RoastMetrics {
  // Increm °T — promedio de °C/min en la ventana móvil, es decir el
  // "velocímetro" de qué tan rápido está calentando el tambor ahora.
  static computeRateOfRisePerMinute(history, nowSeconds) {
    if (!history || history.length < 2) return 0;

    const windowStart = nowSeconds - RATE_OF_RISE_WINDOW_SEC;
    let reference = history[0];
    for (const point of history) {
      if (point.time <= windowStart) {
        reference = point;
      } else {
        break;
      }
    }

    const elapsed = nowSeconds - reference.time;
    if (elapsed <= 0) return 0;

    const current = history[history.length - 1];
    const ratePerSecond = (current.temp - reference.temp) / elapsed;
    return Math.round(ratePerSecond * 60);
  }

  // Tiempo Tueste — minutos decimales (ej. 5.38 = 5 min + 38/100 min),
  // igual al formato del contador en pantalla de la tostadora real.
  static formatDecimalMinutes(totalSeconds) {
    return (totalSeconds / 60).toFixed(2);
  }

  static formatClockTime(totalSeconds) {
    const mins = Math.floor(totalSeconds / 60);
    const secs = Math.floor(totalSeconds % 60);
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
}
