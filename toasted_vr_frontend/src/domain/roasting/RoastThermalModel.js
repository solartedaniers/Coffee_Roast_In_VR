import {
  AUTO_MODE_BASE_POWER_PCT,
  AUTO_MODE_GAIN_PCT_PER_C,
  AUTO_MODE_DAMPING_PCT_PER_C_PER_MIN,
  AUTO_MODE_MAX_POWER_PCT,
  FLAME_POWER_MIN_PCT,
} from './RoastConstants';
import RoastAirModel from './RoastAirModel';
import BeanHeatTransferCalculator from './BeanHeatTransferCalculator';
import MoistureModel from './MoistureModel';

// ================================================================
// RoastThermalModel
// Orquestador del modelo de dos cuerpos: avanza un paso la
// temperatura del aire (RoastAirModel) y, con eso, la del grano
// (BeanHeatTransferCalculator + MoistureModel). Puro y sin estado —
// cada método recibe lo que necesita y devuelve un valor derivado.
// Sin React, sin efectos secundarios.
// ================================================================
export default class RoastThermalModel {
  // airResponseRatePerSec es opcional (default = la tasa de carga/tueste ya
  // validada en RoastAirModel) — el precalentamiento pasa la suya propia,
  // más lenta, a través de RoastPacingProfile.js.
  static computeNextAirTemp(currentAirTemp, flamePowerPercent, thermalMassFactor, airResponseRatePerSec) {
    const targetAirTemp = RoastAirModel.computeAirTargetTemp(flamePowerPercent);
    return RoastAirModel.computeNextAirTemp(currentAirTemp, targetAirTemp, thermalMassFactor, airResponseRatePerSec);
  }

  static computeNextBeanTemp({ airTemp, beanTemp, density, moisturePct, thermalMassFactor, firstCrackReached }) {
    return BeanHeatTransferCalculator.computeNextBeanTemp({
      airTemp,
      beanTemp,
      density,
      moisturePct,
      thermalMassFactor,
      firstCrackReached,
    });
  }

  static computeNextMoisture(currentMoisturePct, beanTemp) {
    return MoistureModel.computeNextMoisture(currentMoisturePct, beanTemp);
  }

  // Controlador proporcional-derivativo usado solo en modo AUTO: pide
  // más llama mientras más lejos está la temperatura de Temp Ctrl,
  // pero se adelanta y suelta potencia en cuanto el café ya está
  // subiendo rápido (término derivativo, sobre Increm °T), sin
  // esperar a que la temperatura realmente cruce el objetivo. Nunca
  // pide más del techo prudente del modo automático.
  static computeAutoFlamePowerPercent(currentTemp, targetTemp, rateOfRisePerMinute) {
    const error = targetTemp - currentTemp;
    const power =
      AUTO_MODE_BASE_POWER_PCT +
      AUTO_MODE_GAIN_PCT_PER_C * error -
      AUTO_MODE_DAMPING_PCT_PER_C_PER_MIN * rateOfRisePerMinute;
    return Math.min(AUTO_MODE_MAX_POWER_PCT, Math.max(FLAME_POWER_MIN_PCT, Math.round(power)));
  }
}
