import { AMBIENT_TEMP_C, MAX_AIR_TEMP_C, AIR_RESPONSE_RATE_PER_SEC, MIN_AIR_TEMP_C } from './RoastConstants';

// ================================================================
// RoastAirModel
// Responsabilidad única: la temperatura del aire/tambor (equivalente
// a ET). Persigue un objetivo que depende solo de la potencia del
// quemador, con una inercia proporcional a la masa térmica del lote —
// el "cuerpo 1" del modelo de dos cuerpos (ecuación 3.1). Puro y sin
// estado, igual que RoastThermalModel.
// ================================================================
export default class RoastAirModel {
  static computeAirTargetTemp(burnerPowerPercent) {
    return AMBIENT_TEMP_C + (burnerPowerPercent / 100) * (MAX_AIR_TEMP_C - AMBIENT_TEMP_C);
  }

  // El aire persigue su objetivo de forma asintótica: nunca lo supera, así
  // que no hace falta un techo superior — solo un piso en la temperatura
  // ambiente. airResponseRatePerSec es configurable (default = la tasa de
  // carga/tueste ya validada) porque el precalentamiento usa su propia
  // tasa, mucho más lenta — ver RoastPacingProfile.js.
  static computeNextAirTemp(currentAirTemp, targetAirTemp, thermalMassFactor, airResponseRatePerSec = AIR_RESPONSE_RATE_PER_SEC) {
    const next =
      currentAirTemp + (targetAirTemp - currentAirTemp) * airResponseRatePerSec * thermalMassFactor;
    return parseFloat(Math.max(MIN_AIR_TEMP_C, next).toFixed(2));
  }
}
