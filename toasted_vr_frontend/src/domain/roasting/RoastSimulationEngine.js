import { PHASES, OPERATION_MODES, AMBIENT_TEMP_C, FLAME_POWER_DEFAULT_PCT } from './RoastConstants';
import RoastThermalModel from './RoastThermalModel';
import RoastMetrics from './RoastMetrics';
import FirstCrackDetector from './FirstCrackDetector';
import SecondCrackDetector from './SecondCrackDetector';
import BurnRiskMonitor from './BurnRiskMonitor';
import MaillardStagnationMonitor from './MaillardStagnationMonitor';
import GreenBeanProfileFactory from './GreenBeanProfileFactory';
import RoastPacingProfile from './RoastPacingProfile';
import ChargeDipCalculator from './ChargeDipCalculator';
import ProbeTemperatureModel from './ProbeTemperatureModel';
import TurningPointDetector from './TurningPointDetector';
import RoastTraceRecorder from './RoastTraceRecorder';

// ================================================================
// RoastSimulationEngine
// Responsabilidad única: el estado del tueste y cómo cambia con cada
// tick y con cada acción del jugador (encender, gas, cargar, descargar).
// No sabe nada del reloj real ni de la pantalla: quien lo usa decide
// cada cuánto llamar a tick (RoastPacingProfile), así la web y Unity
// comparten la misma física.
// ================================================================
export default class RoastSimulationEngine {
  static createInitialState({ chargeTemperature, targetTemperature, operationMode }) {
    const beanProfile = GreenBeanProfileFactory.createRandom();
    return {
      phase: PHASES.IDLE,
      operationMode,
      chargeTemperature,
      targetTemperature,
      beanProfile,
      airTemperature: AMBIENT_TEMP_C,
      temperature: AMBIENT_TEMP_C,
      // Lo que marca el termómetro: el aire antes de cargar y la sonda que
      // persigue al grano después (ProbeTemperatureModel).
      probeTemperature: AMBIENT_TEMP_C,
      // Lectura de la sonda un tick antes de probeTemperature:
      // MaillardStagnationMonitor compara cada lectura nueva con la de dos
      // ticks atrás, igual que las alertas de la IA.
      prevProbeTemperature: AMBIENT_TEMP_C,
      peakTemperature: AMBIENT_TEMP_C,
      finalTemperature: null,
      moisturePct: beanProfile.moisture0Pct,

      flamePowerPercent: FLAME_POWER_DEFAULT_PCT,
      // Si el control automático manejó el gas en algún momento del tueste,
      // la IA no califica las decisiones (RoastTraceRecorder).
      usedAutoMode: false,

      elapsedSeconds: 0,
      roastingElapsedSeconds: 0,
      chargeStartElapsedSeconds: null,
      lowestProbeSinceCharge: null,
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

  static startPreheat(settings) {
    return { ...RoastSimulationEngine.createInitialState(settings), phase: PHASES.PREHEAT };
  }

  static isRunning(phase) {
    return (
      phase === PHASES.PREHEAT || phase === PHASES.READY || phase === PHASES.CHARGE_DIP || phase === PHASES.ROASTING
    );
  }

  static tick(prev) {
    if (!RoastSimulationEngine.isRunning(prev.phase)) {
      return prev;
    }

    // Modelo de dos cuerpos: el aire persigue su objetivo según la
    // potencia del quemador; el grano persigue al aire (solo una vez
    // cargado — antes de eso el grano ni siquiera está en el tambor).
    // La tasa de respuesta del aire depende de la fase (el
    // precalentamiento es mucho más lento/realista que carga/tueste).
    const { thermalMassFactor, density } = prev.beanProfile;
    const { airResponseRatePerSec } = RoastPacingProfile.forPhase(prev.phase);
    const dipLoss =
      prev.phase === PHASES.CHARGE_DIP
        ? ChargeDipCalculator.computeLossPerSecond({
            airTempAtCharge: prev.chargeTemperature,
            flamePowerPercent: prev.flamePowerPercent,
            secondsSinceCharge: prev.roastingElapsedSeconds,
          })
        : 0;
    const newAirTemp = Math.max(
      AMBIENT_TEMP_C,
      RoastThermalModel.computeNextAirTemp(
        prev.airTemperature,
        prev.flamePowerPercent,
        thermalMassFactor,
        airResponseRatePerSec
      ) - dipLoss
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
    const newProbeTemp = beanIsCharged
      ? ProbeTemperatureModel.computeNextProbeTemp(prev.probeTemperature, newTemp)
      : newAirTemp;

    const newLowestProbe =
      prev.phase === PHASES.CHARGE_DIP ? Math.min(prev.lowestProbeSinceCharge, newProbeTemp) : prev.lowestProbeSinceCharge;

    const newElapsed = prev.elapsedSeconds + 1;
    // LISTO cuenta como precalentando para este reloj: el café todavía no
    // se cargó, así que roastingElapsedSeconds debe seguir en 0 aunque la
    // física ya no se detenga al llegar a la temperatura de carga.
    const newRoastingElapsed =
      prev.phase === PHASES.PREHEAT || prev.phase === PHASES.READY ? 0 : prev.roastingElapsedSeconds + 1;

    // Una sola señal, la del termómetro: alimenta el Incremento °C/min y
    // la gráfica, que dibuja una línea continua desde el encendido. Al
    // cargar la sonda se enfría poco a poco hacia el grano, sin saltos.
    const displayTemp = newProbeTemp;
    // gas: la potencia con la que corrió este segundo; la usa la traza del tueste.
    const newSamples = [...prev.samples, { time: newElapsed, temp: displayTemp, gas: prev.flamePowerPercent }];
    const rateOfRisePerMinute = RoastMetrics.computeRateOfRisePerMinute(newSamples, newElapsed);

    // Las reglas leen la sonda, igual que en una tostadora real, y solo
    // desde el punto de retorno: antes la sonda todavía marca el calor del
    // tambor y no la temperatura del café.
    const newConsecutiveBurn =
      prev.phase === PHASES.ROASTING
        ? BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(prev.consecutiveBurnSeconds, newProbeTemp)
        : prev.consecutiveBurnSeconds;
    const newMaxConsecutiveBurn = Math.max(prev.maxConsecutiveBurnSeconds, newConsecutiveBurn);
    const scorchedByRateAndMoisture =
      prev.phase === PHASES.ROASTING &&
      BurnRiskMonitor.isScorchedByRateAndMoisture(rateOfRisePerMinute, newMoisturePct);

    const newStagnationSecs =
      prev.phase === PHASES.ROASTING
        ? MaillardStagnationMonitor.computeNextSeconds(
            prev.maillardStagnationSeconds,
            newProbeTemp,
            prev.prevProbeTemperature,
            prev.firstCrackReached
          )
        : prev.maillardStagnationSeconds;
    const newStagnationFlag = prev.maillardStagnationFlag || MaillardStagnationMonitor.isBaked(newStagnationSecs);

    const hitFirstCrack =
      prev.phase === PHASES.ROASTING &&
      !prev.firstCrackReached &&
      prev.firstCrackThresholdTemp != null &&
      FirstCrackDetector.hasReachedFirstCrack(newProbeTemp, prev.firstCrackThresholdTemp);

    const hitSecondCrack =
      prev.phase === PHASES.ROASTING &&
      (prev.firstCrackReached || hitFirstCrack) &&
      !prev.secondCrackReached &&
      prev.secondCrackThresholdTemp != null &&
      SecondCrackDetector.hasReachedSecondCrack(newProbeTemp, prev.secondCrackThresholdTemp);
    const newSecondCrackReached = prev.secondCrackReached || hitSecondCrack;

    const newBurnedFlag =
      prev.burnedFlag ||
      (prev.phase === PHASES.ROASTING && BurnRiskMonitor.isBurned(newProbeTemp, newSecondCrackReached)) ||
      scorchedByRateAndMoisture;

    const nextFlamePowerPercent =
      prev.operationMode === OPERATION_MODES.AUTO
        ? RoastThermalModel.computeAutoFlamePowerPercent(newProbeTemp, prev.targetTemperature, rateOfRisePerMinute)
        : prev.flamePowerPercent;

    let nextPhase = prev.phase;
    if (prev.phase === PHASES.PREHEAT && newAirTemp >= prev.chargeTemperature) {
      nextPhase = PHASES.READY;
    } else if (
      prev.phase === PHASES.CHARGE_DIP &&
      TurningPointDetector.hasReachedTurningPoint(newProbeTemp, newLowestProbe)
    ) {
      nextPhase = PHASES.ROASTING;
    }

    return {
      ...prev,
      phase: nextPhase,
      airTemperature: newAirTemp,
      temperature: newTemp,
      probeTemperature: newProbeTemp,
      lowestProbeSinceCharge: newLowestProbe,
      prevProbeTemperature: prev.probeTemperature,
      peakTemperature:
        prev.phase === PHASES.ROASTING ? Math.max(prev.peakTemperature, newProbeTemp) : prev.peakTemperature,
      moisturePct: newMoisturePct,
      flamePowerPercent: nextFlamePowerPercent,
      usedAutoMode: prev.usedAutoMode || (beanIsCharged && prev.operationMode === OPERATION_MODES.AUTO),
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
      secondCrackReached: newSecondCrackReached,
      secondCrackTimeSeconds: hitSecondCrack ? newRoastingElapsed : prev.secondCrackTimeSeconds,
    };
  }

  static setFlamePower(prev, flamePowerPercent) {
    return { ...prev, flamePowerPercent };
  }

  static charge(prev) {
    return {
      ...prev,
      phase: PHASES.CHARGE_DIP,
      // La temperatura de carga que cuenta para el puntaje y la IA es la
      // del aire en el momento real de cargar, no la meta del slider.
      chargeTemperature: prev.airTemperature,
      // Punto de partida real del grano al tocar el tambor: el piso efectivo
      // según qué tan lejos estaba el aire de la zona segura al cargar
      // (ChargeDipCalculator.computeEffectiveChargeFloor).
      temperature: ChargeDipCalculator.computeEffectiveChargeFloor(prev.airTemperature, prev.beanProfile.entryTempC),
      chargeStartElapsedSeconds: prev.elapsedSeconds,
      lowestProbeSinceCharge: prev.probeTemperature,
      firstCrackThresholdTemp: FirstCrackDetector.pickThresholdTemperature(prev.beanProfile.density),
      secondCrackThresholdTemp: SecondCrackDetector.pickThresholdTemperature(prev.beanProfile.density),
    };
  }

  // En el tick del punto de retorno el pico aún no cuenta y la sesión no pasaría la validación del backend.
  static canDischarge(state) {
    return state.phase === PHASES.ROASTING && state.peakTemperature >= state.probeTemperature;
  }

  static discharge(current) {
    return {
      ...current,
      phase: PHASES.FINISHED,
      finalTemperature: current.probeTemperature,
    };
  }

  // Lo que se guarda en POST /api/v1/roasting/sessions a partir del tueste
  // ya descargado y de su nota de RoastQualityEvaluator.
  static buildSessionRequest(finishedSim, scoreResult) {
    return {
      chargeTemperature: finishedSim.chargeTemperature,
      targetTemperature: finishedSim.targetTemperature,
      totalDurationSeconds: Math.max(1, Math.round(finishedSim.roastingElapsedSeconds)),
      finalTemperature: finishedSim.finalTemperature,
      peakTemperature: finishedSim.peakTemperature,
      result: scoreResult.result,
      qualityScore: scoreResult.score,
      firstCrackReached: finishedSim.firstCrackReached,
      developmentTimeSeconds: finishedSim.firstCrackReached
        ? Math.max(0, Math.round(finishedSim.roastingElapsedSeconds - (finishedSim.firstCrackTimeSeconds || 0)))
        : 0,
      roastTrace: RoastTraceRecorder.build(finishedSim),
    };
  }
}
