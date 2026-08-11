import React, { useState } from 'react';
import EditableNumberField from './EditableNumberField';
import RoastStatusBadge from './RoastStatusBadge';
import RoastMenuPanel from './RoastMenuPanel';
import TemperatureUnitConverter from '../../domain/roasting/TemperatureUnitConverter';
import {
  TARGET_TEMP_MIN_C,
  TARGET_TEMP_MAX_C,
  FLAME_POWER_MIN_PCT,
  FLAME_POWER_MAX_PCT,
  OPERATION_MODES,
} from '../../domain/roasting/RoastConstants';

// ================================================================
// ManualControlPanel
// Responsabilidad única: el bloque "CONTROL DE TUESTE MANUAL" del
// HMI — indicador de estado, botón de menú y las cinco casillas del
// operador (Temp Ctrl, Increm °T, Potencia % Llama, Temp café,
// Tiempo Tueste). Aquí también se aplican la unidad de temperatura
// (°C/°F) y el offset de calibración, por ser un tema de presentación.
// ================================================================
export default function ManualControlPanel({
  texts,
  phase,
  statusLabel,
  operationMode,
  targetTemperature,
  onCommitTargetTemperature,
  rateOfRisePerMinute,
  flamePowerPercent,
  onCommitFlamePower,
  beanTemperature,
  sensorCalibrationOffsetC = 0,
  temperatureUnit,
  roastTimeDisplay,
  onOpenSettings,
  onResetChart,
  onAbort,
}) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const isManual = operationMode === OPERATION_MODES.MANUAL;
  const unitSymbol = TemperatureUnitConverter.unitSymbol(temperatureUnit);

  const displayTargetTemp = Math.round(TemperatureUnitConverter.toDisplay(targetTemperature, temperatureUnit));
  const displayTargetMin = Math.round(TemperatureUnitConverter.toDisplay(TARGET_TEMP_MIN_C, temperatureUnit));
  const displayTargetMax = Math.round(TemperatureUnitConverter.toDisplay(TARGET_TEMP_MAX_C, temperatureUnit));
  const displayBeanTemp = Math.round(
    TemperatureUnitConverter.toDisplay(beanTemperature + sensorCalibrationOffsetC, temperatureUnit)
  );

  return (
    <div className="manual-control-panel">
      <div className="manual-control-header">
        <RoastStatusBadge phase={phase} label={statusLabel} />

        <div className="roast-menu-anchor">
          <button
            type="button"
            className="secondary-button roast-menu-button"
            onClick={() => setIsMenuOpen((open) => !open)}
          >
            {texts.menuButton}
          </button>
          <RoastMenuPanel
            texts={texts.menu}
            isOpen={isMenuOpen}
            onClose={() => setIsMenuOpen(false)}
            onOpenSettings={() => {
              setIsMenuOpen(false);
              onOpenSettings();
            }}
            onResetChart={() => {
              setIsMenuOpen(false);
              onResetChart();
            }}
            onAbort={() => {
              setIsMenuOpen(false);
              onAbort();
            }}
          />
        </div>
      </div>

      <div className="hmi-tile-grid">
        <EditableNumberField
          label={texts.targetTempTile}
          value={displayTargetTemp}
          unit={unitSymbol}
          min={displayTargetMin}
          max={displayTargetMax}
          editable
          tone="neutral"
          onCommit={(value) => onCommitTargetTemperature(TemperatureUnitConverter.toCelsius(value, temperatureUnit))}
          editAriaLabel={texts.targetTempEditAria}
        />

        <EditableNumberField
          label={texts.rateOfRiseTile}
          value={rateOfRisePerMinute}
          unit={texts.rateOfRiseUnit}
          tone="info"
        />

        <EditableNumberField
          label={texts.flamePowerTile}
          value={flamePowerPercent}
          unit="%"
          min={FLAME_POWER_MIN_PCT}
          max={FLAME_POWER_MAX_PCT}
          editable={isManual}
          tone="flame"
          onCommit={onCommitFlamePower}
          editAriaLabel={texts.flamePowerEditAria}
        />

        <EditableNumberField
          label={texts.beanTempTile}
          value={displayBeanTemp}
          unit={unitSymbol}
          tone="live"
        />

        <EditableNumberField
          label={texts.roastTimeTile}
          value={roastTimeDisplay}
          unit={texts.roastTimeUnit}
          tone="live"
        />
      </div>
    </div>
  );
}
