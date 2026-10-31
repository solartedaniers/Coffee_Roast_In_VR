import { PROBE_RESPONSE_TIME_CONSTANT_SEC } from './RoastConstants';

// ================================================================
// ProbeTemperatureModel
// Responsabilidad única: lo que marca la sonda metida en la masa de
// grano. No calienta ni enfría nada: solo persigue la temperatura del
// grano con un retraso de primer orden, un paso por segundo simulado.
// ================================================================
export default class ProbeTemperatureModel {
  static computeNextProbeTemp(currentProbeTemp, beanTemp) {
    const next = currentProbeTemp + (beanTemp - currentProbeTemp) / PROBE_RESPONSE_TIME_CONSTANT_SEC;
    return parseFloat(next.toFixed(2));
  }
}
