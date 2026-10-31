import { CHART_SAMPLE_INTERVAL_SECONDS } from './RoastConstants';

// ================================================================
// ChartSampleSelector
// Responsabilidad única: elegir de las muestras por segundo las que
// dibuja la gráfica. No toca las muestras: el Incremento °C/min y la
// traza siguen usando todas.
// ================================================================
export default class ChartSampleSelector {
  static select(samples) {
    return samples.filter((sample) => sample.time % CHART_SAMPLE_INTERVAL_SECONDS === 0);
  }
}
