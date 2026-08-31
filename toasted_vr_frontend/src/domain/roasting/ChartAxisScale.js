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
  // minObservedTemp asegura que el piso del eje nunca quede por encima
  // del dato más bajo que hay que dibujar (ej. la temperatura ambiente al
  // encender, o la caída al cargar el café) — sin esto, un dato bajo cae
  // fuera del área dibujada porque el piso solo se calculaba a partir del
  // techo. Sigue dando CHART_VERTICAL_TICK_COUNT marcas como mínimo; solo
  // agrega más hacia abajo cuando el dato realmente lo necesita.
  static computeVerticalTicks(minObservedTemp, maxObservedTemp, stepC = CHART_VERTICAL_STEP_C) {
    const headRoom = stepC;
    const topTick = Math.max(
      stepC * CHART_VERTICAL_TICK_COUNT,
      Math.ceil((maxObservedTemp + headRoom) / stepC) * stepC
    );
    const defaultBottomTick = topTick - (CHART_VERTICAL_TICK_COUNT - 1) * stepC;
    const bottomTick = Math.min(
      defaultBottomTick,
      Math.max(0, Math.floor((minObservedTemp - headRoom) / stepC) * stepC)
    );

    const ticks = [];
    for (let tick = bottomTick; tick <= topTick; tick += stepC) {
      ticks.push(tick);
    }
    return ticks;
  }
}
