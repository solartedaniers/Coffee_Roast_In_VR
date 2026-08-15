import { CHART_VERTICAL_STEP_C, CHART_VERTICAL_TICK_COUNT } from './RoastConstants';

// ================================================================
// ChartAxisScale
// Responsabilidad única: convertir el rango de temperatura actual
// en el conjunto de líneas de la cuadrícula que usa la pantalla del
// HMI — siempre CHART_VERTICAL_TICK_COUNT números, siempre separados
// por CHART_VERTICAL_STEP_C, desplazándose hacia arriba conforme
// sube la temperatura del tueste.
// ================================================================
export default class ChartAxisScale {
  static computeVerticalTicks(maxObservedTemp, stepC = CHART_VERTICAL_STEP_C) {
    const headRoom = stepC;
    const topTick = Math.max(
      stepC * CHART_VERTICAL_TICK_COUNT,
      Math.ceil((maxObservedTemp + headRoom) / stepC) * stepC
    );

    const ticks = [];
    for (let i = CHART_VERTICAL_TICK_COUNT - 1; i >= 0; i--) {
      ticks.push(topTick - i * stepC);
    }
    return ticks;
  }

  // El dominio no siempre arranca en 0: una vez que la ventana móvil
  // de la gráfica se activa, minObservedMinutes avanza junto con el
  // tueste, y las marcas del eje deben reflejar ese avance (hacer
  // scroll) en vez de reiniciarse.
  static computeHorizontalTicks(minObservedMinutes, maxObservedMinutes, tickCount) {
    const span = Math.max(maxObservedMinutes - minObservedMinutes, tickCount);
    const step = Math.max(1, Math.ceil(span / tickCount));
    const ticks = [];
    for (let i = 0; i <= tickCount; i++) {
      ticks.push(minObservedMinutes + Math.min(span, i * step));
    }
    return ticks;
  }
}
