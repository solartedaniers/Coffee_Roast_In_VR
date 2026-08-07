import React from 'react';
import GrainAppearanceModel from '../../domain/roasting/GrainAppearanceModel';
import TemperatureUnitConverter from '../../domain/roasting/TemperatureUnitConverter';
import { MAX_SAFE_TEMP_C, BURN_THRESHOLD_TEMP_C } from '../../domain/roasting/RoastConstants';

// ================================================================
// RoastOvenVisual
// Responsabilidad única: la ilustración del tambor — brillo, granos,
// humo y termómetro — derivada solo de la fase actual y la
// temperatura del grano. El color/brillo/humo siempre reaccionan al
// valor real en Celsius; solo el número del termómetro se convierte
// a la unidad que eligió el operador.
// ================================================================
export default function RoastOvenVisual({
  phase,
  temperature,
  showBeans,
  grainStateLabel,
  isIdle,
  sensorCalibrationOffsetC = 0,
  temperatureUnit,
}) {
  const grainColor = GrainAppearanceModel.interpolateColor(temperature);
  const smokeLevel = GrainAppearanceModel.getSmokeLevel(temperature);
  const isBurntSmoke = GrainAppearanceModel.isBurntSmoke(temperature);
  const showSmoke = showBeans && smokeLevel !== 'none';
  const ovenGlowOpacity = Math.min(0.6, Math.max(0, (temperature - 50) / 200));
  const thermFillPct = Math.max(2, (temperature / MAX_SAFE_TEMP_C) * 100);
  const isOverBurnThreshold = temperature >= BURN_THRESHOLD_TEMP_C;
  const displayTemp = Math.round(
    TemperatureUnitConverter.toDisplay(temperature + sensorCalibrationOffsetC, temperatureUnit)
  );

  return (
    <div className="sim-oven-wrapper">
      <div
        className={`oven-body${!isIdle ? ' oven-active' : ''}`}
        style={{ '--oven-glow-opacity': ovenGlowOpacity }}
      >
        <div className="oven-window">
          {!isIdle && <div className="oven-heat-glow" style={{ opacity: ovenGlowOpacity }} />}

          {showBeans && (
            <div className="beans-cluster">
              {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                <div key={i} className={`coffee-bean bean-pos-${i}`} style={{ backgroundColor: grainColor }} />
              ))}
            </div>
          )}

          {showSmoke && (
            <div className={`smoke-container smoke-${smokeLevel}${isBurntSmoke ? ' smoke-burnt' : ''}`}>
              {[0, 1, 2].map((i) => (
                <div key={i} className="smoke-puff" />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="sim-thermometer">
        <div className="therm-track">
          <div
            className={`therm-fill${isOverBurnThreshold ? ' therm-fill-danger' : ''}`}
            style={{ height: `${thermFillPct}%` }}
          />
        </div>
        <span className="therm-label">{displayTemp}{TemperatureUnitConverter.unitSymbol(temperatureUnit)}</span>
      </div>

      {showBeans && <span className="sim-grain-state">{grainStateLabel}</span>}
    </div>
  );
}
