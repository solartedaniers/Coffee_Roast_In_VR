import { CHART_SMOOTHING_MAX_DROP_C_PER_SAMPLE } from './RoastConstants';

// ================================================================
// ChartSmoothingFilter
// Responsabilidad única: suavizar caídas bruscas de la línea dibujada
// (ej. al cargar el grano frío en el tambor caliente) para que no se
// vea como un diente agudo en la gráfica. Nunca toca los datos que
// recibe — devuelve un arreglo nuevo, así que las muestras crudas que
// alimentan el panel, el puntaje y el log siguen intactas.
// ================================================================
export default class ChartSmoothingFilter {
  static smooth(samples) {
    if (samples.length === 0) return samples;

    const smoothed = [samples[0]];
    for (let i = 1; i < samples.length; i++) {
      const prevTemp = smoothed[i - 1].temp;
      const rawTemp = samples[i].temp;
      const drop = prevTemp - rawTemp;
      const temp =
        drop > CHART_SMOOTHING_MAX_DROP_C_PER_SAMPLE ? prevTemp - CHART_SMOOTHING_MAX_DROP_C_PER_SAMPLE : rawTemp;
      smoothed.push({ ...samples[i], temp });
    }
    return smoothed;
  }
}
