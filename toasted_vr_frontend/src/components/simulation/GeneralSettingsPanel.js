import React from 'react';
import {
  OPERATION_MODES,
  TEMPERATURE_UNITS,
  SENSOR_CALIBRATION_MIN_C,
  SENSOR_CALIBRATION_MAX_C,
  ALARM_LIMIT_MIN_C,
  ALARM_LIMIT_MAX_C,
  CHART_STEP_OPTIONS_C,
} from '../../domain/roasting/RoastConstants';

// ================================================================
// GeneralSettingsPanel
// Responsabilidad única: la pantalla "Configuración general" que se
// abre desde el menú del HMI — calibración de sensores, alarmas de
// seguridad, modo de operación y preferencias de visualización/unidades.
// Solo renderiza ajustes del equipo y avisa los cambios hacia arriba;
// no toca el estado de la simulación.
// ================================================================
export default function GeneralSettingsPanel({
  texts,
  isOpen,
  onClose,
  operationMode,
  onChangeOperationMode,
  temperatureUnit,
  onChangeTemperatureUnit,
  sensorCalibrationOffsetC,
  onChangeSensorCalibrationOffset,
  alarmLimitTempC,
  onChangeAlarmLimitTemp,
  chartStepC,
  onChangeChartStepC,
}) {
  if (!isOpen) return null;

  return (
    <div className="profile-modal-backdrop" role="dialog" aria-modal="true" aria-label={texts.title}>
      <div className="profile-modal settings-modal">
        <div className="profile-modal-header">
          <button type="button" className="secondary-button profile-close-button" onClick={onClose}>
            {texts.close}
          </button>
        </div>
        <h2>{texts.title}</h2>
        <p className="sim-hint-text">{texts.subtitle}</p>

        <section className="settings-section">
          <h3 className="profile-section-title">{texts.calibration.title}</h3>
          <p className="sim-hint-text">{texts.calibration.description}</p>
          <label className="settings-field">
            <span>{texts.calibration.offsetLabel}</span>
            <input
              type="number"
              className="field-input"
              min={SENSOR_CALIBRATION_MIN_C}
              max={SENSOR_CALIBRATION_MAX_C}
              step={0.5}
              value={sensorCalibrationOffsetC}
              onChange={(event) => onChangeSensorCalibrationOffset(Number(event.target.value))}
            />
          </label>
        </section>

        <section className="settings-section">
          <h3 className="profile-section-title">{texts.alarms.title}</h3>
          <p className="sim-hint-text">{texts.alarms.description}</p>
          <label className="settings-field">
            <span>{texts.alarms.limitLabel}</span>
            <input
              type="number"
              className="field-input"
              min={ALARM_LIMIT_MIN_C}
              max={ALARM_LIMIT_MAX_C}
              step={1}
              value={alarmLimitTempC}
              onChange={(event) => onChangeAlarmLimitTemp(Number(event.target.value))}
            />
          </label>
        </section>

        <section className="settings-section">
          <h3 className="profile-section-title">{texts.mode.title}</h3>
          <p className="sim-hint-text">{texts.mode.description}</p>
          <div className="roast-menu-mode-toggle">
            <button
              type="button"
              className={`roast-menu-mode-btn${operationMode === OPERATION_MODES.MANUAL ? ' roast-menu-mode-btn-active' : ''}`}
              onClick={() => onChangeOperationMode(OPERATION_MODES.MANUAL)}
            >
              {texts.mode.manual}
            </button>
            <button
              type="button"
              className={`roast-menu-mode-btn${operationMode === OPERATION_MODES.AUTO ? ' roast-menu-mode-btn-active' : ''}`}
              onClick={() => onChangeOperationMode(OPERATION_MODES.AUTO)}
            >
              {texts.mode.auto}
            </button>
          </div>
        </section>

        <section className="settings-section">
          <h3 className="profile-section-title">{texts.display.title}</h3>
          <p className="sim-hint-text">{texts.display.description}</p>
          <div className="settings-field-row">
            <label className="settings-field">
              <span>{texts.display.unitLabel}</span>
              <div className="roast-menu-mode-toggle">
                <button
                  type="button"
                  className={`roast-menu-mode-btn${temperatureUnit === TEMPERATURE_UNITS.CELSIUS ? ' roast-menu-mode-btn-active' : ''}`}
                  onClick={() => onChangeTemperatureUnit(TEMPERATURE_UNITS.CELSIUS)}
                >
                  °C
                </button>
                <button
                  type="button"
                  className={`roast-menu-mode-btn${temperatureUnit === TEMPERATURE_UNITS.FAHRENHEIT ? ' roast-menu-mode-btn-active' : ''}`}
                  onClick={() => onChangeTemperatureUnit(TEMPERATURE_UNITS.FAHRENHEIT)}
                >
                  °F
                </button>
              </div>
            </label>
            <label className="settings-field settings-field-narrow">
              <span>{texts.display.chartStepLabel}</span>
              <select
                className="field-input"
                value={chartStepC}
                onChange={(event) => onChangeChartStepC(Number(event.target.value))}
              >
                {CHART_STEP_OPTIONS_C.map((step) => (
                  <option key={step} value={step}>
                    {step}°C
                  </option>
                ))}
              </select>
            </label>
          </div>
        </section>
      </div>
    </div>
  );
}
