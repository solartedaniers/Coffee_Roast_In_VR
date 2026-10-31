import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import './RoastingSimulation.css';
import ProfileSettings from '../ProfileSettings';
import ManualControlPanel from './ManualControlPanel';
import TemperatureChart from './TemperatureChart';
import RoastOvenVisual from './RoastOvenVisual';
import RoastResultsPanel from './RoastResultsPanel';
import GeneralSettingsPanel from './GeneralSettingsPanel';
import CurveProgrammingPanel from './CurveProgrammingPanel';
import PlayerProgressButtons from '../progress/PlayerProgressButtons';
import HelpPanel from './HelpPanel';
import { saveRoastingSession, getRoastingFeedback } from '../../services/simulationService';

import {
  PHASES,
  OPERATION_MODES,
  TEMPERATURE_UNITS,
  CHARGE_TEMP_MIN_C,
  CHARGE_TEMP_MAX_C,
  CHARGE_TEMP_DEFAULT_C,
  TARGET_TEMP_MIN_C,
  TARGET_TEMP_MAX_C,
  TARGET_TEMP_DEFAULT_C,
  BURN_WARNING_TEMP_C,
  CRACK_SOUND_PATH,
  SENSOR_CALIBRATION_DEFAULT_C,
  ALARM_LIMIT_DEFAULT_C,
  CHART_VERTICAL_STEP_C,
  CHART_TIME_WINDOW_SECONDS,
  FIRST_CRACK_TEMP_MIN_C,
  FIRST_CRACK_TEMP_MAX_C,
  FIRST_CRACK_TEMP_MIDPOINT_C,
  CHARGE_TEMP_IDEAL_MAX_C,
  BURN_ABSOLUTE_CEILING_TEMP_C,
} from '../../domain/roasting/RoastConstants';
import RoastMetrics from '../../domain/roasting/RoastMetrics';
import RoastQualityEvaluator from '../../domain/roasting/RoastQualityEvaluator';
import GrainAppearanceModel from '../../domain/roasting/GrainAppearanceModel';
import KnowledgeLevelRules from '../../domain/roasting/KnowledgeLevelRules';
import RoastCurveProfile from '../../domain/roasting/RoastCurveProfile';
import RoastPacingProfile from '../../domain/roasting/RoastPacingProfile';
import ChartSampleSelector from '../../domain/roasting/ChartSampleSelector';
import RoastSimulationEngine from '../../domain/roasting/RoastSimulationEngine';

// Valores de ejemplo para las 3 curvas de "Programación de Curvas" —
// reutilizan constantes de dominio ya existentes en vez de inventar
// números nuevos. Las curvas todavía no se aplican a ningún tueste real
// (ver RoastCurveProfile.js).
function createDefaultRoastCurves() {
  return [
    new RoastCurveProfile({
      curveNumber: 1,
      startTemp: CHARGE_TEMP_DEFAULT_C,
      crackleTemp: FIRST_CRACK_TEMP_MIN_C,
      finalTemp: Math.round(FIRST_CRACK_TEMP_MIDPOINT_C),
    }),
    new RoastCurveProfile({
      curveNumber: 2,
      startTemp: CHARGE_TEMP_DEFAULT_C,
      crackleTemp: Math.round(FIRST_CRACK_TEMP_MIDPOINT_C),
      finalTemp: FIRST_CRACK_TEMP_MAX_C,
    }),
    new RoastCurveProfile({
      curveNumber: 3,
      startTemp: CHARGE_TEMP_IDEAL_MAX_C,
      crackleTemp: FIRST_CRACK_TEMP_MAX_C,
      finalTemp: BURN_ABSOLUTE_CEILING_TEMP_C - 3,
    }),
  ];
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
    RoastSimulationEngine.createInitialState({
      chargeTemperature: chargeTempSetup,
      targetTemperature: targetTempSetup,
      operationMode: OPERATION_MODES.MANUAL,
    })
  );
  const [roastResult, setRoastResult] = useState(null);
  const [savingState, setSavingState] = useState('idle');
  const [savedSession, setSavedSession] = useState(null);
  const [saveErrorDetail, setSaveErrorDetail] = useState('');
  const [feedbackState, setFeedbackState] = useState('idle');
  const [feedbackText, setFeedbackText] = useState('');
  const [showCrackAlert, setShowCrackAlert] = useState(false);
  const [showSecondCrackAlert, setShowSecondCrackAlert] = useState(false);
  const [showBurnedAlert, setShowBurnedAlert] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isCurveProgrammingOpen, setIsCurveProgrammingOpen] = useState(false);
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const [roastCurves, setRoastCurves] = useState(createDefaultRoastCurves);

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
  const secondCrackFiredRef = useRef(false);
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
    setSimState(RoastSimulationEngine.tick);
  }, []);

  // LISTO ya NO detiene el reloj: el aire debe seguir respondiendo a la
  // potencia (subir o bajar) hasta que el usuario decida cargar el café —
  // llegar a la temperatura de carga solo enciende la alerta visual
  // (showChargeOvershootAlert, ya reacciona sola a la fase LISTO).

  // La transición CHARGE_DIP → ROASTING ocurre sola dentro de runTick (no
  // por un clic del usuario, a diferencia de handleStartPreheat/
  // handleLoadBeans), así que es acá donde hay que acelerar el reloj real
  // de vuelta al ritmo normal justo al entrar a tueste.
  useEffect(() => {
    if (simState.phase === PHASES.ROASTING && intervalRef.current) {
      stopInterval();
      intervalRef.current = setInterval(runTick, RoastPacingProfile.forPhase(PHASES.ROASTING).tickIntervalMs);
    }
  }, [simState.phase, stopInterval, runTick]);

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

  // Second crack: mismo patrón que el first crack, reutiliza el mismo
  // efecto de sonido (no hay un asset propio para el segundo crack).
  useEffect(() => {
    if (simState.secondCrackReached && !secondCrackFiredRef.current) {
      secondCrackFiredRef.current = true;
      setShowSecondCrackAlert(true);
      setTimeout(() => setShowSecondCrackAlert(false), 5000);
      try {
        const audio = new Audio(CRACK_SOUND_PATH);
        audio.play().catch(() => {});
      } catch (_) {}
    }
  }, [simState.secondCrackReached]);

  // Quemado: aviso único al pasar del techo, llegar al segundo crack o subir agresivo con el grano seco
  useEffect(() => {
    if (simState.burnedFlag && !burnedFiredRef.current) {
      burnedFiredRef.current = true;
      setShowBurnedAlert(true);
      setTimeout(() => setShowBurnedAlert(false), 6000);
    }
  }, [simState.burnedFlag]);

  // Alarma de seguridad del equipo: se dispara cada vez que la temperatura
  // cruza el límite configurado por el operador, independiente de la
  // regla de café quemado (BURN_ABSOLUTE_CEILING_TEMP_C).
  useEffect(() => {
    // Mira lo que marca el termómetro: el aire antes de cargar y la sonda
    // del grano después.
    const isPreCharge = simState.phase === PHASES.PREHEAT || simState.phase === PHASES.READY;
    const isPostCharge = simState.phase === PHASES.CHARGE_DIP || simState.phase === PHASES.ROASTING;
    const isOverAlarm = (isPreCharge || isPostCharge) && simState.probeTemperature >= alarmLimitTempC;

    if (isOverAlarm && !alarmFiredRef.current) {
      alarmFiredRef.current = true;
      setShowAlarmAlert(true);
      setTimeout(() => setShowAlarmAlert(false), 6000);
    } else if (!isOverAlarm) {
      alarmFiredRef.current = false;
    }
  }, [simState.phase, simState.probeTemperature, alarmLimitTempC]);

  useEffect(() => () => stopInterval(), [stopInterval]);

  // ── Manejadores de eventos ──────────────────────────────────────

  const handleStartPreheat = () => {
    crackFiredRef.current = false;
    secondCrackFiredRef.current = false;
    burnedFiredRef.current = false;
    setRoastResult(null);
    setSavingState('idle');
    setSavedSession(null);
    setSimState(
      RoastSimulationEngine.startPreheat({
        chargeTemperature: chargeTempSetup,
        targetTemperature: targetTempSetup,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    intervalRef.current = setInterval(runTick, RoastPacingProfile.forPhase(PHASES.PREHEAT).tickIntervalMs);
  };

  const handleLoadBeans = () => {
    setSimState(RoastSimulationEngine.charge);
    // Ahora se puede cargar desde PRECALENTANDO, cuando el intervalo del
    // precalentamiento todavía puede estar corriendo (antes solo se podía
    // cargar en LISTO, donde ya estaba detenido) — se detiene primero para
    // no terminar con dos intervalos duplicando cada tick.
    stopInterval();
    intervalRef.current = setInterval(runTick, RoastPacingProfile.forPhase(PHASES.CHARGE_DIP).tickIntervalMs);
  };

  const handleCommitTargetTemperature = useCallback((value) => {
    setSimState((prev) => ({ ...prev, targetTemperature: value }));
  }, []);

  const handleCommitFlamePower = useCallback((value) => {
    setSimState((prev) => RoastSimulationEngine.setFlamePower(prev, value));
  }, []);

  const handleChangeOperationMode = useCallback((mode) => {
    setSimState((prev) => ({ ...prev, operationMode: mode }));
  }, []);

  // "Control de Tueste Automático" del menú: cambia a modo AUTO y abre la
  // selección de curvas en el mismo clic — el subtítulo del recuadro
  // ("Selec de Curvas de Tueste") es literalmente lo que hace.
  const handleOpenAutoControl = useCallback(() => {
    handleChangeOperationMode(OPERATION_MODES.AUTO);
    setIsCurveProgrammingOpen(true);
  }, [handleChangeOperationMode]);

  // "Control de Tueste Manual" del menú: el espejo del anterior — cambia a
  // modo MANUAL directo, sin panel propio (ya es la pantalla que se ve).
  const handleOpenManualControl = useCallback(() => {
    handleChangeOperationMode(OPERATION_MODES.MANUAL);
  }, [handleChangeOperationMode]);

  const handleChangeCurve = useCallback((curveNumber, field, value) => {
    setRoastCurves((prev) =>
      prev.map((curve) =>
        curve.curveNumber === curveNumber ? new RoastCurveProfile({ ...curve, [field]: value }) : curve
      )
    );
  }, []);

  const handleResetChartView = useCallback(() => {
    setSimState((prev) => ({ ...prev, chartViewResetAtSeconds: prev.elapsedSeconds }));
  }, []);

  const handleAbort = useCallback(() => {
    stopInterval();
    setSimState(
      RoastSimulationEngine.createInitialState({
        chargeTemperature: chargeTempSetup,
        targetTemperature: targetTempSetup,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    setRoastResult(null);
    setSavingState('idle');
    setSavedSession(null);
    setSaveErrorDetail('');
    setFeedbackState('idle');
    setFeedbackText('');
  }, [stopInterval, chargeTempSetup, targetTempSetup]);

  const handleDischarge = useCallback(async () => {
    stopInterval();
    const current = latestSimRef.current;
    const finalSim = RoastSimulationEngine.discharge(current);
    const scoreResult = RoastQualityEvaluator.evaluate(finalSim, currentUser.knowledgeLevel);

    setSimState(finalSim);
    setRoastResult(scoreResult);
    setSavingState('saving');
    setSavedSession(null);
    setSaveErrorDetail('');

    try {
      const saved = await saveRoastingSession(RoastSimulationEngine.buildSessionRequest(finalSim, scoreResult));
      setSavingState('saved');
      setSavedSession(saved);

      setFeedbackState('loading');
      try {
        const feedback = await getRoastingFeedback(saved.id);
        if (feedback) {
          setFeedbackText(feedback);
          setFeedbackState('ready');
        } else {
          setFeedbackState('unavailable');
        }
      } catch (feedbackError) {
        // La retroalimentación de IA es un plus sobre el resultado ya
        // guardado — si Ollama no responde, no debe romper el flujo.
        console.warn('No se pudo generar retroalimentación:', feedbackError);
        setFeedbackState('unavailable');
      }
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
    secondCrackFiredRef.current = false;
    burnedFiredRef.current = false;
    stopInterval();
    setChargeTempSetup(CHARGE_TEMP_DEFAULT_C);
    setTargetTempSetup(TARGET_TEMP_DEFAULT_C);
    setSimState(
      RoastSimulationEngine.createInitialState({
        chargeTemperature: CHARGE_TEMP_DEFAULT_C,
        targetTemperature: TARGET_TEMP_DEFAULT_C,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    setRoastResult(null);
    setSavingState('idle');
    setSavedSession(null);
    setSaveErrorDetail('');
    setFeedbackState('idle');
    setFeedbackText('');
  };

  // ── Valores derivados para el render ────────────────────────────

  const s = simState;
  const grainStateLabel = texts.grainStates[GrainAppearanceModel.getGrainStateName(s.probeTemperature, s.firstCrackReached)];
  const showBeans =
    s.phase === PHASES.CHARGE_DIP || s.phase === PHASES.ROASTING || s.phase === PHASES.FINISHED;
  const guidanceText = getGuidanceText(texts, s.phase, currentUser.knowledgeLevel);
  // Durante el precalentamiento, roastingElapsedSeconds se mantiene en 0 a
  // propósito (no cuenta el precalentamiento ni la espera en LISTO) — para
  // que el reloj mostrado suba de verdad hacia los 8-10 min simulados
  // mientras se precalienta, y siga subiendo aunque ya se haya alcanzado
  // la temperatura de carga, se muestra elapsedSeconds (el tiempo total
  // desde el encendido) en ambas fases; el resto del tiempo vuelve a ser
  // el contador real del tueste.
  const roastTimeDisplay = RoastMetrics.formatDecimalMinutes(
    s.phase === PHASES.PREHEAT || s.phase === PHASES.READY ? s.elapsedSeconds : s.roastingElapsedSeconds
  );

  // Luz de sobrecalentamiento de precalentado: relativa a la meta que el
  // propio usuario fijó en el slider (s.chargeTemperature), no a un
  // umbral fijo — se apaga sola en cuanto el aire vuelve a bajar de ese
  // valor, sin necesidad de un temporizador.
  const showChargeOvershootAlert =
    (s.phase === PHASES.PREHEAT || s.phase === PHASES.READY) && s.airTemperature > s.chargeTemperature;

  const ovenDisplayTemp = s.probeTemperature;

  // La gráfica dibuja la lectura del termómetro desde el encendido del
  // horno (ver displayTemp en runTick). El tiempo es relativo al
  // encendido (elapsedSeconds ya arranca en 0 ahí).
  const chartSamples = useMemo(() => ChartSampleSelector.select(s.samples), [s.samples]);
  const chartPoints = useMemo(() => {
    const windowStart = Math.max(0, s.chartViewResetAtSeconds ?? -Infinity, s.elapsedSeconds - CHART_TIME_WINDOW_SECONDS);
    return chartSamples.filter((sample) => sample.time >= windowStart);
  }, [chartSamples, s.chartViewResetAtSeconds, s.elapsedSeconds]);

  const isFinished = s.phase === PHASES.FINISHED;
  // s.elapsedSeconds (no roastingElapsedSeconds): la gráfica arranca en el
  // encendido, así que el criterio de "tueste largo" debe usar el mismo
  // reloj que el filtro de chartPoints (más abajo) — si no, el precalentado
  // se recorta cuando el total pasa de 6 min aunque la parte cargada sola
  // no llegue a esa marca.
  const isLongFinishedRoast = isFinished && s.elapsedSeconds > CHART_TIME_WINDOW_SECONDS;
  const displayedChartPoints = isLongFinishedRoast ? chartSamples : chartPoints;
  const chartTitle = isFinished ? texts.chart.titleFinal : texts.chart.title;

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

      <CurveProgrammingPanel
        texts={texts.curveProgramming}
        isOpen={isCurveProgrammingOpen}
        onClose={() => setIsCurveProgrammingOpen(false)}
        curves={roastCurves}
        onChangeCurve={handleChangeCurve}
      />

      <HelpPanel texts={texts.help} isOpen={isHelpOpen} onClose={() => setIsHelpOpen(false)} />

      {/* ── Cabecera ───────────────────────────────────────── */}
      <header className="sim-header">
        <div className="sim-header-brand">
          <span className="sim-brand-name">{texts.title.split(' ').slice(0, 2).join(' ')}</span>
          <span className="sim-batch-badge">{texts.labels.batchWeight}: {s.beanProfile.batchWeightKg} kg</span>
        </div>

        <div className="sim-header-status">
          <span className="sim-phase-badge">{texts.phases[s.phase]}</span>
        </div>

        <div className="sim-header-user">
          <PlayerProgressButtons texts={texts.dataHistory} currentUser={currentUser} />
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
      {showSecondCrackAlert && (
        <div className="sim-crack-alert" role="alert" aria-live="assertive">
          {texts.labels.secondCrack}
        </div>
      )}
      {showChargeOvershootAlert && (
        <div className="sim-charge-overheat-alert" role="alert" aria-live="assertive">
          {texts.labels.chargeOverheatAlert}
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
      {s.phase === PHASES.ROASTING && s.probeTemperature >= BURN_WARNING_TEMP_C && !s.burnedFlag && (
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
            temperature={ovenDisplayTemp}
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
              beanTemperature={showBeans ? s.probeTemperature : 0}
              sensorCalibrationOffsetC={sensorCalibrationOffsetC}
              temperatureUnit={temperatureUnit}
              roastTimeDisplay={roastTimeDisplay}
              onOpenAutoControl={handleOpenAutoControl}
              onOpenManualControl={handleOpenManualControl}
              onOpenSettings={() => setIsSettingsOpen(true)}
              onOpenHelp={() => setIsHelpOpen(true)}
              onResetChart={handleResetChartView}
              onAbort={handleAbort}
            />
          )}

          {(s.phase === PHASES.PREHEAT || s.phase === PHASES.READY) && (
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
      {displayedChartPoints.length > 1 && (
        <div className="sim-chart-section">
          <h3 className="sim-section-title">{chartTitle}</h3>
          <TemperatureChart
            data={displayedChartPoints}
            targetTemperature={s.targetTemperature}
            sensorCalibrationOffsetC={sensorCalibrationOffsetC}
            temperatureUnit={temperatureUnit}
            chartStepC={chartStepC}
            texts={texts.chart}
            fitToData={isLongFinishedRoast}
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
          feedbackState={feedbackState}
          feedbackText={feedbackText}
          temperatureUnit={temperatureUnit}
          texts={texts.results}
          savedSession={savedSession}
          evaluationTexts={texts.dataHistory}
          onNewSimulation={handleNewSimulation}
        />
      )}
    </div>
  );
}
