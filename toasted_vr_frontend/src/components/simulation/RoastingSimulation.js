import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import './RoastingSimulation.css';
import ProfileSettings from '../ProfileSettings';
import ManualControlPanel from './ManualControlPanel';
import TemperatureChart from './TemperatureChart';
import RoastOvenVisual from './RoastOvenVisual';
import RoastResultsPanel from './RoastResultsPanel';
import GeneralSettingsPanel from './GeneralSettingsPanel';
import { saveRoastingSession } from '../../services/simulationService';

import {
  PHASES,
  OPERATION_MODES,
  TEMPERATURE_UNITS,
  BATCH_WEIGHT_KG,
  AMBIENT_TEMP_C,
  CHARGE_TEMP_MIN_C,
  CHARGE_TEMP_MAX_C,
  CHARGE_TEMP_DEFAULT_C,
  TARGET_TEMP_MIN_C,
  TARGET_TEMP_MAX_C,
  TARGET_TEMP_DEFAULT_C,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_DIP_DURATION_SEC,
  BURN_THRESHOLD_TEMP_C,
  MAILLARD_TEMP_START_C,
  MAILLARD_TEMP_END_C,
  MAILLARD_STAGNATION_LIMIT_SEC,
  CRACK_SOUND_PATH,
  SENSOR_CALIBRATION_DEFAULT_C,
  ALARM_LIMIT_DEFAULT_C,
  CHART_VERTICAL_STEP_C,
} from '../../domain/roasting/RoastConstants';
import RoastThermalModel from '../../domain/roasting/RoastThermalModel';
import RoastMetrics from '../../domain/roasting/RoastMetrics';
import FirstCrackDetector from '../../domain/roasting/FirstCrackDetector';
import BurnRiskMonitor from '../../domain/roasting/BurnRiskMonitor';
import RoastQualityEvaluator from '../../domain/roasting/RoastQualityEvaluator';
import GrainAppearanceModel from '../../domain/roasting/GrainAppearanceModel';
import KnowledgeLevelRules from '../../domain/roasting/KnowledgeLevelRules';

function createInitialSimState({ chargeTemperature, targetTemperature, operationMode }) {
  return {
    phase: PHASES.IDLE,
    operationMode,
    chargeTemperature,
    targetTemperature,
    temperature: AMBIENT_TEMP_C,
    prevTemperature: AMBIENT_TEMP_C,
    peakTemperature: AMBIENT_TEMP_C,
    finalTemperature: null,

    flamePowerPercent: FLAME_POWER_DEFAULT_PCT,
    effectiveFlamePowerPercent: 0,

    elapsedSeconds: 0,
    roastingElapsedSeconds: 0,
    chargeStartElapsedSeconds: null,
    chartViewResetAtSeconds: null,
    samples: [],

    rateOfRisePerMinute: 0,

    firstCrackThresholdTemp: null,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,

    consecutiveBurnSeconds: 0,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,

    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
}

function getGuidanceText(texts, phase, knowledgeLevel) {
  const rules = KnowledgeLevelRules.forLevel(knowledgeLevel);
  if (rules.guidanceMode === 'hidden') return null;
  return texts.guidance?.[rules.guidanceMode]?.[phase] || null;
}

// ================================================================
// RoastingSimulation
// Orquestador: controla el reloj y el estado de la simulación,
// delega toda la matemática de dominio a las clases de roasting/*
// y toda la renderización a los componentes de presentación de abajo.
// ================================================================
export default function RoastingSimulation({
  texts,
  profileTexts,
  knowledgeTexts,
  currentUser,
  onLogout,
  onUserUpdate,
}) {
  const [chargeTempSetup, setChargeTempSetup] = useState(CHARGE_TEMP_DEFAULT_C);
  const [targetTempSetup, setTargetTempSetup] = useState(TARGET_TEMP_DEFAULT_C);

  const [simState, setSimState] = useState(() =>
    createInitialSimState({
      chargeTemperature: chargeTempSetup,
      targetTemperature: targetTempSetup,
      operationMode: OPERATION_MODES.MANUAL,
    })
  );
  const [roastResult, setRoastResult] = useState(null);
  const [savingState, setSavingState] = useState('idle');
  const [saveErrorDetail, setSaveErrorDetail] = useState('');
  const [showCrackAlert, setShowCrackAlert] = useState(false);
  const [showBurnedAlert, setShowBurnedAlert] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  // Ajustes del equipo: viven en el menú de configuración general y
  // persisten entre tuestes (a diferencia de simState, que se reinicia
  // en cada nueva simulación).
  const [temperatureUnit, setTemperatureUnit] = useState(TEMPERATURE_UNITS.CELSIUS);
  const [sensorCalibrationOffsetC, setSensorCalibrationOffsetC] = useState(SENSOR_CALIBRATION_DEFAULT_C);
  const [alarmLimitTempC, setAlarmLimitTempC] = useState(ALARM_LIMIT_DEFAULT_C);
  const [chartStepC, setChartStepC] = useState(CHART_VERTICAL_STEP_C);
  const [showAlarmAlert, setShowAlarmAlert] = useState(false);

  const intervalRef = useRef(null);
  const crackFiredRef = useRef(false);
  const burnedFiredRef = useRef(false);
  const alarmFiredRef = useRef(false);
  const latestSimRef = useRef(simState);

  useEffect(() => {
    latestSimRef.current = simState;
  }, [simState]);

  const stopInterval = useCallback(() => {
    clearInterval(intervalRef.current);
    intervalRef.current = null;
  }, []);

  // ── Tick de la simulación ───────────────────────────────────────
  const runTick = useCallback(() => {
    setSimState((prev) => {
      if (
        prev.phase !== PHASES.PREHEAT &&
        prev.phase !== PHASES.CHARGE_DIP &&
        prev.phase !== PHASES.ROASTING
      ) {
        return prev;
      }

      const effectivePower = RoastThermalModel.computeEffectivePower(
        prev.effectiveFlamePowerPercent,
        prev.flamePowerPercent
      );
      const baseRate = RoastThermalModel.computeHeatRatePerSecond(effectivePower, prev.temperature);
      const dipLoss =
        prev.phase === PHASES.CHARGE_DIP
          ? RoastThermalModel.computeChargeDipLossPerSecond(prev.roastingElapsedSeconds)
          : 0;
      const newTemp = RoastThermalModel.computeNextTemperature(prev.temperature, baseRate - dipLoss);

      const newElapsed = prev.elapsedSeconds + 1;
      const newRoastingElapsed =
        prev.phase === PHASES.PREHEAT ? 0 : prev.roastingElapsedSeconds + 1;

      const newSamples = [...prev.samples, { time: newElapsed, temp: newTemp }];
      const rateOfRisePerMinute = RoastMetrics.computeRateOfRisePerMinute(newSamples, newElapsed);

      const newConsecutiveBurn = BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(
        prev.consecutiveBurnSeconds,
        newTemp
      );
      const newMaxConsecutiveBurn = Math.max(prev.maxConsecutiveBurnSeconds, newConsecutiveBurn);
      const newBurnedFlag = prev.burnedFlag || BurnRiskMonitor.isBurned(newConsecutiveBurn);

      let newStagnationSecs = prev.maillardStagnationSeconds;
      let newStagnationFlag = prev.maillardStagnationFlag;
      if (prev.phase === PHASES.ROASTING) {
        const inMaillard = newTemp >= MAILLARD_TEMP_START_C && newTemp <= MAILLARD_TEMP_END_C;
        const tempDropped = newTemp <= prev.prevTemperature;
        if (inMaillard && tempDropped) {
          newStagnationSecs += 1;
          if (newStagnationSecs >= MAILLARD_STAGNATION_LIMIT_SEC) newStagnationFlag = true;
        } else if (inMaillard) {
          newStagnationSecs = 0;
        }
      }

      const hitFirstCrack =
        prev.phase === PHASES.ROASTING &&
        !prev.firstCrackReached &&
        prev.firstCrackThresholdTemp != null &&
        FirstCrackDetector.hasReachedFirstCrack(newTemp, prev.firstCrackThresholdTemp);

      const nextFlamePowerPercent =
        prev.operationMode === OPERATION_MODES.AUTO
          ? RoastThermalModel.computeAutoFlamePowerPercent(newTemp, prev.targetTemperature, rateOfRisePerMinute)
          : prev.flamePowerPercent;

      let nextPhase = prev.phase;
      if (prev.phase === PHASES.PREHEAT && newTemp >= prev.chargeTemperature) {
        nextPhase = PHASES.READY;
      } else if (prev.phase === PHASES.CHARGE_DIP && newRoastingElapsed >= CHARGE_DIP_DURATION_SEC) {
        nextPhase = PHASES.ROASTING;
      }

      return {
        ...prev,
        phase: nextPhase,
        temperature: newTemp,
        prevTemperature: prev.temperature,
        peakTemperature: Math.max(prev.peakTemperature, newTemp),
        effectiveFlamePowerPercent: effectivePower,
        flamePowerPercent: nextFlamePowerPercent,
        elapsedSeconds: newElapsed,
        roastingElapsedSeconds: newRoastingElapsed,
        samples: newSamples,
        rateOfRisePerMinute,
        consecutiveBurnSeconds: newConsecutiveBurn,
        maxConsecutiveBurnSeconds: newMaxConsecutiveBurn,
        burnedFlag: newBurnedFlag,
        maillardStagnationSeconds: newStagnationSecs,
        maillardStagnationFlag: newStagnationFlag,
        firstCrackReached: prev.firstCrackReached || hitFirstCrack,
        firstCrackTimeSeconds: hitFirstCrack ? newRoastingElapsed : prev.firstCrackTimeSeconds,
      };
    });
  }, []);

  // Detiene el reloj cuando el tambor llega a la temperatura de carga y espera el café
  useEffect(() => {
    if (simState.phase === PHASES.READY) {
      stopInterval();
    }
  }, [simState.phase, stopInterval]);

  // First crack: aviso y sonido, una sola vez por tueste
  useEffect(() => {
    if (simState.firstCrackReached && !crackFiredRef.current) {
      crackFiredRef.current = true;
      setShowCrackAlert(true);
      setTimeout(() => setShowCrackAlert(false), 5000);
      try {
        const audio = new Audio(CRACK_SOUND_PATH);
        audio.play().catch(() => {});
      } catch (_) {}
    }
  }, [simState.firstCrackReached]);

  // Quemado: aviso único al confirmarse la racha de 30 s sobre 200°C
  useEffect(() => {
    if (simState.burnedFlag && !burnedFiredRef.current) {
      burnedFiredRef.current = true;
      setShowBurnedAlert(true);
      setTimeout(() => setShowBurnedAlert(false), 6000);
    }
  }, [simState.burnedFlag]);

  // Alarma de seguridad del equipo: se dispara cada vez que la temperatura
  // cruza el límite configurado por el operador, independiente de la
  // regla fija de café quemado (BURN_THRESHOLD_TEMP_C, que nunca cambia).
  useEffect(() => {
    const isOverAlarm =
      (simState.phase === PHASES.PREHEAT ||
        simState.phase === PHASES.CHARGE_DIP ||
        simState.phase === PHASES.ROASTING) &&
      simState.temperature >= alarmLimitTempC;

    if (isOverAlarm && !alarmFiredRef.current) {
      alarmFiredRef.current = true;
      setShowAlarmAlert(true);
      setTimeout(() => setShowAlarmAlert(false), 6000);
    } else if (!isOverAlarm) {
      alarmFiredRef.current = false;
    }
  }, [simState.phase, simState.temperature, alarmLimitTempC]);

  useEffect(() => () => stopInterval(), [stopInterval]);

  // ── Manejadores de eventos ──────────────────────────────────────

  const handleStartPreheat = () => {
    crackFiredRef.current = false;
    burnedFiredRef.current = false;
    setRoastResult(null);
    setSavingState('idle');
    setSimState({
      ...createInitialSimState({
        chargeTemperature: chargeTempSetup,
        targetTemperature: targetTempSetup,
        operationMode: OPERATION_MODES.MANUAL,
      }),
      phase: PHASES.PREHEAT,
    });
    intervalRef.current = setInterval(runTick, 1000);
  };

  const handleLoadBeans = () => {
    setSimState((prev) => ({
      ...prev,
      phase: PHASES.CHARGE_DIP,
      chargeStartElapsedSeconds: prev.elapsedSeconds,
      firstCrackThresholdTemp: FirstCrackDetector.pickThresholdTemperature(),
    }));
    intervalRef.current = setInterval(runTick, 1000);
  };

  const handleCommitTargetTemperature = useCallback((value) => {
    setSimState((prev) => ({ ...prev, targetTemperature: value }));
  }, []);

  const handleCommitFlamePower = useCallback((value) => {
    setSimState((prev) => ({ ...prev, flamePowerPercent: value }));
  }, []);

  const handleChangeOperationMode = useCallback((mode) => {
    setSimState((prev) => ({ ...prev, operationMode: mode }));
  }, []);

  const handleResetChartView = useCallback(() => {
    setSimState((prev) => ({ ...prev, chartViewResetAtSeconds: prev.elapsedSeconds }));
  }, []);

  const handleAbort = useCallback(() => {
    stopInterval();
    setSimState(
      createInitialSimState({
        chargeTemperature: chargeTempSetup,
        targetTemperature: targetTempSetup,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    setRoastResult(null);
    setSavingState('idle');
    setSaveErrorDetail('');
  }, [stopInterval, chargeTempSetup, targetTempSetup]);

  const handleDischarge = useCallback(async () => {
    stopInterval();
    const current = latestSimRef.current;
    const finalSim = {
      ...current,
      phase: PHASES.FINISHED,
      finalTemperature: current.temperature,
    };
    const scoreResult = RoastQualityEvaluator.evaluate(finalSim, currentUser.knowledgeLevel);

    setSimState(finalSim);
    setRoastResult(scoreResult);
    setSavingState('saving');
    setSaveErrorDetail('');

    try {
      await saveRoastingSession({
        targetTemperature: finalSim.targetTemperature,
        totalDurationSeconds: Math.max(1, Math.round(finalSim.roastingElapsedSeconds)),
        finalTemperature: finalSim.finalTemperature,
        peakTemperature: finalSim.peakTemperature,
        result: scoreResult.result,
        qualityScore: scoreResult.score,
        firstCrackReached: finalSim.firstCrackReached,
        developmentTimeSeconds: finalSim.firstCrackReached
          ? Math.max(0, Math.round(finalSim.roastingElapsedSeconds - (finalSim.firstCrackTimeSeconds || 0)))
          : 0,
      });
      setSavingState('saved');
    } catch (error) {
      // Se muestra el error real (antes se descartaba) para poder
      // diagnosticar un guardado fallido desde la consola/red del navegador.
      console.error('No se pudo guardar la sesión de tueste:', error);
      setSaveErrorDetail(error?.message || '');
      setSavingState('error');
    }
  }, [currentUser.knowledgeLevel, stopInterval]);

  const handleNewSimulation = () => {
    crackFiredRef.current = false;
    burnedFiredRef.current = false;
    stopInterval();
    setChargeTempSetup(CHARGE_TEMP_DEFAULT_C);
    setTargetTempSetup(TARGET_TEMP_DEFAULT_C);
    setSimState(
      createInitialSimState({
        chargeTemperature: CHARGE_TEMP_DEFAULT_C,
        targetTemperature: TARGET_TEMP_DEFAULT_C,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    setRoastResult(null);
    setSavingState('idle');
    setSaveErrorDetail('');
  };

  // ── Valores derivados para el render ────────────────────────────

  const s = simState;
  const grainStateLabel = texts.grainStates[GrainAppearanceModel.getGrainStateName(s.temperature)];
  const showBeans =
    s.phase === PHASES.CHARGE_DIP || s.phase === PHASES.ROASTING || s.phase === PHASES.FINISHED;
  const guidanceText = getGuidanceText(texts, s.phase, currentUser.knowledgeLevel);
  const roastTimeDisplay = RoastMetrics.formatDecimalMinutes(s.roastingElapsedSeconds);

  const chartPoints = useMemo(() => {
    if (s.chargeStartElapsedSeconds == null) return [];
    const origin = Math.max(s.chargeStartElapsedSeconds, s.chartViewResetAtSeconds ?? -Infinity);
    return s.samples
      .filter((sample) => sample.time >= origin)
      .map((sample) => ({ time: sample.time - origin, temp: sample.temp }));
  }, [s.samples, s.chargeStartElapsedSeconds, s.chartViewResetAtSeconds]);

  const chargeSliderPct =
    ((chargeTempSetup - CHARGE_TEMP_MIN_C) / (CHARGE_TEMP_MAX_C - CHARGE_TEMP_MIN_C)) * 100;
  const targetSliderPct =
    ((targetTempSetup - TARGET_TEMP_MIN_C) / (TARGET_TEMP_MAX_C - TARGET_TEMP_MIN_C)) * 100;

  const showManualPanel =
    s.phase === PHASES.PREHEAT ||
    s.phase === PHASES.READY ||
    s.phase === PHASES.CHARGE_DIP ||
    s.phase === PHASES.ROASTING;

  return (
    <div className="sim-layout">
      <ProfileSettings
        texts={profileTexts}
        knowledgeTexts={knowledgeTexts}
        currentUser={currentUser}
        isOpen={isProfileOpen}
        onClose={() => setIsProfileOpen(false)}
        onUserUpdate={onUserUpdate}
      />

      <GeneralSettingsPanel
        texts={texts.settings}
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        operationMode={s.operationMode}
        onChangeOperationMode={handleChangeOperationMode}
        temperatureUnit={temperatureUnit}
        onChangeTemperatureUnit={setTemperatureUnit}
        sensorCalibrationOffsetC={sensorCalibrationOffsetC}
        onChangeSensorCalibrationOffset={setSensorCalibrationOffsetC}
        alarmLimitTempC={alarmLimitTempC}
        onChangeAlarmLimitTemp={setAlarmLimitTempC}
        chartStepC={chartStepC}
        onChangeChartStepC={setChartStepC}
      />

      {/* ── Cabecera ───────────────────────────────────────── */}
      <header className="sim-header">
        <div className="sim-header-brand">
          <span className="sim-brand-name">{texts.title.split(' ').slice(0, 2).join(' ')}</span>
          <span className="sim-batch-badge">{texts.labels.batchWeight}: {BATCH_WEIGHT_KG} kg</span>
        </div>

        <div className="sim-header-status">
          <span className="sim-phase-badge">{texts.phases[s.phase]}</span>
        </div>

        <div className="sim-header-user">
          <button
            type="button"
            className="sim-user-chip"
            onClick={() => setIsProfileOpen(true)}
            aria-label={profileTexts.buttons.open}
          >
            <div className="sim-user-avatar">
              {currentUser.profileImageUrl ? (
                <img src={currentUser.profileImageUrl} alt={profileTexts.photo.alt} />
              ) : (
                <span>{currentUser.name.charAt(0).toUpperCase()}</span>
              )}
            </div>
            <span className="sim-username-label">{currentUser.username}</span>
          </button>
          <button type="button" className="secondary-button sim-logout-btn" onClick={onLogout}>
            {texts.buttons.logout}
          </button>
        </div>
      </header>

      {guidanceText && (
        <section className="sim-guidance-panel" aria-live="polite">
          <span className="sim-section-title">{texts.guidance.title}</span>
          <p>{guidanceText}</p>
        </section>
      )}

      {/* ── Avisos ─────────────────────────────────────────── */}
      {showCrackAlert && (
        <div className="sim-crack-alert" role="alert" aria-live="assertive">
          {texts.labels.firstCrack}
        </div>
      )}
      {showBurnedAlert && (
        <div className="sim-burned-alert" role="alert" aria-live="assertive">
          {texts.labels.burnedNotice}
        </div>
      )}
      {showAlarmAlert && (
        <div className="sim-burned-alert" role="alert" aria-live="assertive">
          {texts.labels.alarmNotice}
        </div>
      )}
      {s.phase === PHASES.ROASTING && s.temperature >= BURN_THRESHOLD_TEMP_C && !s.burnedFlag && (
        <div className="sim-burn-alert" role="alert" aria-live="polite">
          {texts.labels.burnAlert}
        </div>
      )}

      {/* ── Cuadrícula principal: horno + controles ─────────── */}
      <div className="sim-main-grid">
        <div className="sim-oven-col">
          <span className="sim-section-label">{texts.labels.grainState}</span>
          <RoastOvenVisual
            phase={s.phase}
            temperature={s.temperature}
            showBeans={showBeans}
            grainStateLabel={grainStateLabel}
            isIdle={s.phase === PHASES.IDLE}
            sensorCalibrationOffsetC={sensorCalibrationOffsetC}
            temperatureUnit={temperatureUnit}
          />
        </div>

        <div className="sim-controls-col">
          {s.phase === PHASES.IDLE && (
            <div className="control-section">
              <div className="control-section-title">{texts.labels.chargeTemperature}</div>
              <div className="target-temp-control">
                <span className="temp-range-label">{CHARGE_TEMP_MIN_C}°C</span>
                <input
                  type="range"
                  className="temp-slider"
                  min={CHARGE_TEMP_MIN_C}
                  max={CHARGE_TEMP_MAX_C}
                  step={1}
                  value={chargeTempSetup}
                  style={{ '--slider-pct': `${chargeSliderPct}%` }}
                  onChange={(e) => setChargeTempSetup(Number(e.target.value))}
                />
                <span className="temp-range-label">{CHARGE_TEMP_MAX_C}°C</span>
              </div>
              <div className="target-temp-display">{chargeTempSetup}°C</div>

              <div className="control-section-title">{texts.labels.targetTemperature}</div>
              <div className="target-temp-control">
                <span className="temp-range-label">{TARGET_TEMP_MIN_C}°C</span>
                <input
                  type="range"
                  className="temp-slider"
                  min={TARGET_TEMP_MIN_C}
                  max={TARGET_TEMP_MAX_C}
                  step={5}
                  value={targetTempSetup}
                  style={{ '--slider-pct': `${targetSliderPct}%` }}
                  onChange={(e) => setTargetTempSetup(Number(e.target.value))}
                />
                <span className="temp-range-label">{TARGET_TEMP_MAX_C}°C</span>
              </div>
              <div className="target-temp-display">{targetTempSetup}°C</div>

              <p className="sim-hint-text">{texts.info.preheatHelp}</p>

              <button type="button" className="primary-button sim-action-btn" onClick={handleStartPreheat}>
                {texts.buttons.startPreheat}
              </button>
            </div>
          )}

          {showManualPanel && (
            <ManualControlPanel
              texts={texts.controlPanel}
              phase={s.phase}
              statusLabel={texts.status[s.phase]}
              operationMode={s.operationMode}
              targetTemperature={s.targetTemperature}
              onCommitTargetTemperature={handleCommitTargetTemperature}
              rateOfRisePerMinute={s.rateOfRisePerMinute}
              flamePowerPercent={Math.round(s.flamePowerPercent)}
              onCommitFlamePower={handleCommitFlamePower}
              beanTemperature={s.temperature}
              sensorCalibrationOffsetC={sensorCalibrationOffsetC}
              temperatureUnit={temperatureUnit}
              roastTimeDisplay={roastTimeDisplay}
              onOpenSettings={() => setIsSettingsOpen(true)}
              onResetChart={handleResetChartView}
              onAbort={handleAbort}
            />
          )}

          {s.phase === PHASES.READY && (
            <div className="control-section">
              <button type="button" className="primary-button sim-action-btn" onClick={handleLoadBeans}>
                {texts.buttons.loadBeans}
              </button>
            </div>
          )}

          {s.phase === PHASES.ROASTING && (
            <div className="control-section">
              <button type="button" className="primary-button sim-action-btn" onClick={handleDischarge}>
                {texts.buttons.dischargeCoffee}
              </button>
            </div>
          )}

          {(s.phase === PHASES.CHARGE_DIP || s.phase === PHASES.ROASTING) && (
            <div className="sim-stats-mini">
              <div className="stat-item">
                <span className="stat-label">{texts.labels.elapsedTime}</span>
                <span className="stat-value">{RoastMetrics.formatClockTime(s.elapsedSeconds)}</span>
              </div>
              {s.firstCrackReached && (
                <div className="stat-item stat-highlight">
                  <span className="stat-label">{texts.labels.firstCrack}</span>
                  <span className="stat-value">{RoastMetrics.formatClockTime(s.firstCrackTimeSeconds)}</span>
                </div>
              )}
              {s.firstCrackReached && (
                <div className="stat-item stat-highlight">
                  <span className="stat-label">{texts.info.developmentTime}</span>
                  <span className="stat-value">
                    {RoastMetrics.formatClockTime(s.roastingElapsedSeconds - (s.firstCrackTimeSeconds || 0))}
                  </span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── Gráfica de temperatura ───────────────────────────── */}
      {chartPoints.length > 1 && (
        <div className="sim-chart-section">
          <h3 className="sim-section-title">{texts.chart.title}</h3>
          <TemperatureChart
            data={chartPoints}
            chargeTemperature={s.chargeTemperature}
            targetTemperature={s.targetTemperature}
            sensorCalibrationOffsetC={sensorCalibrationOffsetC}
            temperatureUnit={temperatureUnit}
            chartStepC={chartStepC}
            texts={texts.chart}
          />
        </div>
      )}

      {/* ── Resultados ─────────────────────────────────────── */}
      {s.phase === PHASES.FINISHED && roastResult && (
        <RoastResultsPanel
          sim={s}
          roastResult={roastResult}
          savingState={savingState}
          saveErrorDetail={saveErrorDetail}
          temperatureUnit={temperatureUnit}
          texts={texts.results}
          onNewSimulation={handleNewSimulation}
        />
      )}
    </div>
  );
}
