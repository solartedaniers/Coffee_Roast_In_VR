import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import './RoastingSimulation.css';
import ProfileSettings from '../ProfileSettings';
import ManualControlPanel from './ManualControlPanel';
import TemperatureChart from './TemperatureChart';
import RoastOvenVisual from './RoastOvenVisual';
import RoastResultsPanel from './RoastResultsPanel';
import GeneralSettingsPanel from './GeneralSettingsPanel';
import { saveRoastingSession, getRoastingFeedback } from '../../services/simulationService';

import {
  PHASES,
  OPERATION_MODES,
  TEMPERATURE_UNITS,
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
  CHART_TIME_WINDOW_SECONDS,
} from '../../domain/roasting/RoastConstants';
import RoastThermalModel from '../../domain/roasting/RoastThermalModel';
import RoastMetrics from '../../domain/roasting/RoastMetrics';
import FirstCrackDetector from '../../domain/roasting/FirstCrackDetector';
import SecondCrackDetector from '../../domain/roasting/SecondCrackDetector';
import BurnRiskMonitor from '../../domain/roasting/BurnRiskMonitor';
import RoastQualityEvaluator from '../../domain/roasting/RoastQualityEvaluator';
import GrainAppearanceModel from '../../domain/roasting/GrainAppearanceModel';
import KnowledgeLevelRules from '../../domain/roasting/KnowledgeLevelRules';
import GreenBeanProfileFactory from '../../domain/roasting/GreenBeanProfileFactory';
import ChartSmoothingFilter from '../../domain/roasting/ChartSmoothingFilter';

// El precalentamiento y la carga terminan en muy pocos segundos simulados
// (el aire responde rápido, a propósito — ver AIR_RESPONSE_RATE_PER_SEC),
// y como cada tick real dura lo mismo en cualquier fase, esas dos fases se
// sienten apuradas comparadas con el tueste. Sin tocar ninguna constante
// de física ya calibrada, se hace que el reloj REAL vaya más lento solo en
// esas fases (2.5x) — el tueste cargado sigue a su ritmo normal de 1
// segundo simulado por segundo real.
const NORMAL_TICK_INTERVAL_MS = 1000;
const SLOW_TICK_INTERVAL_MS = 2500;

function createInitialSimState({ chargeTemperature, targetTemperature, operationMode }) {
  const beanProfile = GreenBeanProfileFactory.createRandom();
  return {
    phase: PHASES.IDLE,
    operationMode,
    chargeTemperature,
    targetTemperature,
    beanProfile,
    airTemperature: AMBIENT_TEMP_C,
    temperature: AMBIENT_TEMP_C,
    prevTemperature: AMBIENT_TEMP_C,
    peakTemperature: AMBIENT_TEMP_C,
    finalTemperature: null,
    moisturePct: beanProfile.moisture0Pct,

    flamePowerPercent: FLAME_POWER_DEFAULT_PCT,

    elapsedSeconds: 0,
    roastingElapsedSeconds: 0,
    chargeStartElapsedSeconds: null,
    chartViewResetAtSeconds: null,
    samples: [],

    rateOfRisePerMinute: 0,

    firstCrackThresholdTemp: null,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,

    secondCrackThresholdTemp: null,
    secondCrackReached: false,
    secondCrackTimeSeconds: null,

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
  const [feedbackState, setFeedbackState] = useState('idle');
  const [feedbackText, setFeedbackText] = useState('');
  const [showCrackAlert, setShowCrackAlert] = useState(false);
  const [showSecondCrackAlert, setShowSecondCrackAlert] = useState(false);
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
    setSimState((prev) => {
      if (
        prev.phase !== PHASES.PREHEAT &&
        prev.phase !== PHASES.CHARGE_DIP &&
        prev.phase !== PHASES.ROASTING
      ) {
        return prev;
      }

      // Modelo de dos cuerpos: el aire persigue su objetivo según la
      // potencia del quemador; el grano persigue al aire (solo una vez
      // cargado — antes de eso el grano ni siquiera está en el tambor).
      const { thermalMassFactor, density } = prev.beanProfile;
      const dipLoss =
        prev.phase === PHASES.CHARGE_DIP
          ? RoastThermalModel.computeChargeDipLossPerSecond(prev.roastingElapsedSeconds)
          : 0;
      const newAirTemp = Math.max(
        AMBIENT_TEMP_C,
        RoastThermalModel.computeNextAirTemp(prev.airTemperature, prev.flamePowerPercent, thermalMassFactor) -
          dipLoss
      );

      const beanIsCharged = prev.phase === PHASES.CHARGE_DIP || prev.phase === PHASES.ROASTING;
      const newTemp = beanIsCharged
        ? RoastThermalModel.computeNextBeanTemp({
            airTemp: newAirTemp,
            beanTemp: prev.temperature,
            density,
            moisturePct: prev.moisturePct,
            thermalMassFactor,
            firstCrackReached: prev.firstCrackReached,
          })
        : prev.temperature;
      const newMoisturePct = beanIsCharged
        ? RoastThermalModel.computeNextMoisture(prev.moisturePct, newTemp)
        : prev.moisturePct;

      const newElapsed = prev.elapsedSeconds + 1;
      const newRoastingElapsed =
        prev.phase === PHASES.PREHEAT ? 0 : prev.roastingElapsedSeconds + 1;

      // Una sola señal, con sentido físico en todo momento: el aire
      // mientras el café no está cargado (es lo único presente en el
      // tambor) y el grano una vez cargado — mismo gate que beanIsCharged.
      // La alimenta tanto el Incremento °C/min como la gráfica (más abajo),
      // que ahora dibuja una sola línea continua desde el encendido — el
      // ChartSmoothingFilter se encarga de que el cambio de identidad en el
      // momento de la carga se vea como un descenso rápido, no un salto.
      const displayTemp = beanIsCharged ? newTemp : newAirTemp;
      const newSamples = [...prev.samples, { time: newElapsed, temp: displayTemp }];
      const rateOfRisePerMinute = RoastMetrics.computeRateOfRisePerMinute(newSamples, newElapsed);

      const newConsecutiveBurn = BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(
        prev.consecutiveBurnSeconds,
        newTemp
      );
      const newMaxConsecutiveBurn = Math.max(prev.maxConsecutiveBurnSeconds, newConsecutiveBurn);
      const scorchedByRateAndMoisture =
        prev.phase === PHASES.ROASTING &&
        BurnRiskMonitor.isScorchedByRateAndMoisture(rateOfRisePerMinute, newMoisturePct);
      const newBurnedFlag =
        prev.burnedFlag || BurnRiskMonitor.isBurned(newConsecutiveBurn) || scorchedByRateAndMoisture;

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

      const hitSecondCrack =
        prev.phase === PHASES.ROASTING &&
        (prev.firstCrackReached || hitFirstCrack) &&
        !prev.secondCrackReached &&
        prev.secondCrackThresholdTemp != null &&
        SecondCrackDetector.hasReachedSecondCrack(newTemp, prev.secondCrackThresholdTemp);

      const nextFlamePowerPercent =
        prev.operationMode === OPERATION_MODES.AUTO
          ? RoastThermalModel.computeAutoFlamePowerPercent(newTemp, prev.targetTemperature, rateOfRisePerMinute)
          : prev.flamePowerPercent;

      let nextPhase = prev.phase;
      if (prev.phase === PHASES.PREHEAT && newAirTemp >= prev.chargeTemperature) {
        nextPhase = PHASES.READY;
      } else if (prev.phase === PHASES.CHARGE_DIP && newRoastingElapsed >= CHARGE_DIP_DURATION_SEC) {
        nextPhase = PHASES.ROASTING;
      }

      return {
        ...prev,
        phase: nextPhase,
        airTemperature: newAirTemp,
        temperature: newTemp,
        prevTemperature: prev.temperature,
        peakTemperature: Math.max(prev.peakTemperature, newTemp),
        moisturePct: newMoisturePct,
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
        secondCrackReached: prev.secondCrackReached || hitSecondCrack,
        secondCrackTimeSeconds: hitSecondCrack ? newRoastingElapsed : prev.secondCrackTimeSeconds,
      };
    });
  }, []);

  // Detiene el reloj cuando el tambor llega a la temperatura de carga y espera el café
  useEffect(() => {
    if (simState.phase === PHASES.READY) {
      stopInterval();
    }
  }, [simState.phase, stopInterval]);

  // La transición CHARGE_DIP → ROASTING ocurre sola dentro de runTick (no
  // por un clic del usuario, a diferencia de handleStartPreheat/
  // handleLoadBeans), así que es acá donde hay que acelerar el reloj real
  // de vuelta al ritmo normal justo al entrar a tueste.
  useEffect(() => {
    if (simState.phase === PHASES.ROASTING && intervalRef.current) {
      stopInterval();
      intervalRef.current = setInterval(runTick, NORMAL_TICK_INTERVAL_MS);
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
    // Durante PREHEAT/READY el grano todavía no está en el tambor (su
    // temperatura queda congelada en ambiente bajo el modelo nuevo), así
    // que la alarma debe mirar la temperatura del aire en esas fases; una
    // vez cargado el café, vuelve a mirar la temperatura del grano.
    const isPreCharge = simState.phase === PHASES.PREHEAT || simState.phase === PHASES.READY;
    const isPostCharge = simState.phase === PHASES.CHARGE_DIP || simState.phase === PHASES.ROASTING;
    const relevantTemp = isPreCharge ? simState.airTemperature : simState.temperature;
    const isOverAlarm = (isPreCharge || isPostCharge) && relevantTemp >= alarmLimitTempC;

    if (isOverAlarm && !alarmFiredRef.current) {
      alarmFiredRef.current = true;
      setShowAlarmAlert(true);
      setTimeout(() => setShowAlarmAlert(false), 6000);
    } else if (!isOverAlarm) {
      alarmFiredRef.current = false;
    }
  }, [simState.phase, simState.temperature, simState.airTemperature, alarmLimitTempC]);

  useEffect(() => () => stopInterval(), [stopInterval]);

  // ── Manejadores de eventos ──────────────────────────────────────

  const handleStartPreheat = () => {
    crackFiredRef.current = false;
    secondCrackFiredRef.current = false;
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
    intervalRef.current = setInterval(runTick, SLOW_TICK_INTERVAL_MS);
  };

  const handleLoadBeans = () => {
    setSimState((prev) => ({
      ...prev,
      phase: PHASES.CHARGE_DIP,
      // Sobrescribe la temperatura de carga "planeada" (la del slider de
      // configuración) por la temperatura real del aire en el momento en
      // que de verdad se cargó — ahora que se puede cargar en cualquier
      // punto del precalentado, es esta lectura real la que debe afectar
      // el puntaje/IA, no la intención original. A partir de aquí,
      // ninguna otra lógica lee chargeTemperature en el sentido de
      // "meta del slider" (la alerta de sobrecalentamiento y la
      // transición automática a LISTO solo miran este campo antes de
      // cargar).
      chargeTemperature: prev.airTemperature,
      chargeStartElapsedSeconds: prev.elapsedSeconds,
      firstCrackThresholdTemp: FirstCrackDetector.pickThresholdTemperature(prev.beanProfile.density),
      secondCrackThresholdTemp: SecondCrackDetector.pickThresholdTemperature(prev.beanProfile.density),
    }));
    // Ahora se puede cargar desde PRECALENTANDO, cuando el intervalo del
    // precalentamiento todavía puede estar corriendo (antes solo se podía
    // cargar en LISTO, donde ya estaba detenido) — se detiene primero para
    // no terminar con dos intervalos duplicando cada tick.
    stopInterval();
    intervalRef.current = setInterval(runTick, SLOW_TICK_INTERVAL_MS);
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
    setFeedbackState('idle');
    setFeedbackText('');
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
      const saved = await saveRoastingSession({
        chargeTemperature: finalSim.chargeTemperature,
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
      createInitialSimState({
        chargeTemperature: CHARGE_TEMP_DEFAULT_C,
        targetTemperature: TARGET_TEMP_DEFAULT_C,
        operationMode: OPERATION_MODES.MANUAL,
      })
    );
    setRoastResult(null);
    setSavingState('idle');
    setSaveErrorDetail('');
    setFeedbackState('idle');
    setFeedbackText('');
  };

  // ── Valores derivados para el render ────────────────────────────

  const s = simState;
  const grainStateLabel = texts.grainStates[GrainAppearanceModel.getGrainStateName(s.temperature)];
  const showBeans =
    s.phase === PHASES.CHARGE_DIP || s.phase === PHASES.ROASTING || s.phase === PHASES.FINISHED;
  const guidanceText = getGuidanceText(texts, s.phase, currentUser.knowledgeLevel);
  const roastTimeDisplay = RoastMetrics.formatDecimalMinutes(s.roastingElapsedSeconds);

  // Luz de sobrecalentamiento de precalentado: relativa a la meta que el
  // propio usuario fijó en el slider (s.chargeTemperature), no a un
  // umbral fijo — se apaga sola en cuanto el aire vuelve a bajar de ese
  // valor, sin necesidad de un temporizador.
  const showChargeOvershootAlert =
    (s.phase === PHASES.PREHEAT || s.phase === PHASES.READY) && s.airTemperature > s.chargeTemperature;

  // El termómetro/gráfica del horno debe mostrar la temperatura del
  // aire/tambor mientras el café no está cargado (es lo único que sube
  // durante el precalentado bajo el modelo de dos cuerpos) y pasar a la
  // temperatura del grano una vez cargado — mismo gate que beanIsCharged
  // en runTick.
  const ovenDisplayTemp =
    s.phase === PHASES.PREHEAT || s.phase === PHASES.READY ? s.airTemperature : s.temperature;

  // La gráfica dibuja una sola línea continua desde el encendido del
  // horno: aire/tambor mientras el café no está cargado, grano después
  // (ver displayTemp en runTick). El "salto" de identidad en el momento
  // de la carga es real (el aire estaba caliente, el grano entra frío),
  // pero ChartSmoothingFilter lo dibuja como un descenso rápido y
  // continuo en vez de un corte vertical — así se ve como el punto de
  // giro de una curva de tueste real, no como un glitch. El tiempo es
  // relativo al encendido (elapsedSeconds ya arranca en 0 ahí).
  const chartPoints = useMemo(() => {
    if (s.samples.length === 0) return [];
    const windowStart = Math.max(0, s.chartViewResetAtSeconds ?? -Infinity, s.elapsedSeconds - CHART_TIME_WINDOW_SECONDS);
    const windowed = s.samples.filter((sample) => sample.time >= windowStart);
    return ChartSmoothingFilter.smooth(windowed);
  }, [s.samples, s.chartViewResetAtSeconds, s.elapsedSeconds]);

  // Curva sin ventana móvil, para la pantalla de resultado final de
  // tuestes largos. Solo se calcula al terminar (s.samples ya no crece
  // en ese momento): mientras se tuesta devuelve un arreglo vacío para
  // no mapear el historial completo en cada muestra nueva.
  const fullRoastChartPoints = useMemo(() => {
    if (s.phase !== PHASES.FINISHED) return [];
    return ChartSmoothingFilter.smooth(s.samples);
  }, [s.phase, s.samples]);

  const isFinished = s.phase === PHASES.FINISHED;
  // s.elapsedSeconds (no roastingElapsedSeconds): la gráfica arranca en el
  // encendido, así que el criterio de "tueste largo" debe usar el mismo
  // reloj que el filtro de chartPoints (más abajo) — si no, el precalentado
  // se recorta cuando el total pasa de 6 min aunque la parte cargada sola
  // no llegue a esa marca.
  const isLongFinishedRoast = isFinished && s.elapsedSeconds > CHART_TIME_WINDOW_SECONDS;
  const displayedChartPoints = isLongFinishedRoast ? fullRoastChartPoints : chartPoints;
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
              beanTemperature={showBeans ? s.temperature : 0}
              sensorCalibrationOffsetC={sensorCalibrationOffsetC}
              temperatureUnit={temperatureUnit}
              roastTimeDisplay={roastTimeDisplay}
              onOpenSettings={() => setIsSettingsOpen(true)}
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
          onNewSimulation={handleNewSimulation}
        />
      )}
    </div>
  );
}
