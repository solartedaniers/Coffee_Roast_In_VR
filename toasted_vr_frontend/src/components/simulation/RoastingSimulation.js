import React, { useState, useEffect, useRef, useCallback } from 'react';
import './RoastingSimulation.css';
import { saveRoastingSession } from '../../services/simulationService';

// ================================================================
// SIMULATION CONSTANTS (physics parameters — not UI colors)
// ================================================================

const AMBIENT_TEMP = 20;
const MAX_SAFE_TEMP = 240;
const PREHEAT_RATE_PER_SECOND = 2;
const COOLDOWN_DURATION_SECONDS = 60;
const COOLDOWN_TOTAL_DROP = 45;
const FIRST_CRACK_TEMP = 180;
const BURN_THRESHOLD_TEMP = 200;
const BURN_ALERT_SECONDS = 15;
const MAILLARD_TEMP_START = 131;
const MAILLARD_TEMP_END = 179;
const MAILLARD_STAGNATION_LIMIT = 30;
const TARGET_TEMP_MIN = 180;
const TARGET_TEMP_MAX = 220;
const TARGET_TEMP_DEFAULT = 200;
const CRACK_SOUND_PATH = `${process.env.PUBLIC_URL}/assets/sounds/crack.mp3`;

const PHASES = Object.freeze({
  IDLE: 'IDLE',
  PREHEAT: 'PREHEAT',
  READY: 'READY',
  COOLDOWN: 'COOLDOWN',
  ROASTING: 'ROASTING',
  FINISHED: 'FINISHED',
});

const POWER = Object.freeze({
  OFF: 'OFF',
  LOW: 'LOW',
  MEDIUM: 'MEDIUM',
  HIGH: 'HIGH',
});

const TEMP_RATE_PER_SECOND = Object.freeze({
  [POWER.OFF]: -1.5,
  [POWER.LOW]: -0.2,
  [POWER.MEDIUM]: 0.7,
  [POWER.HIGH]: 1.5,
});

// [temp_threshold, [r, g, b]] — physical color of coffee beans at each temperature
const GRAIN_COLOR_STOPS = [
  [20, [112, 130, 56]],
  [130, [230, 214, 144]],
  [179, [179, 139, 109]],
  [180, [123, 63, 0]],
  [199, [59, 34, 25]],
  [200, [26, 17, 16]],
];

// ================================================================
// PURE UTILITY FUNCTIONS
// ================================================================

function interpolateGrainColor(temperature) {
  const stops = GRAIN_COLOR_STOPS;
  if (temperature <= stops[0][0]) {
    const [r, g, b] = stops[0][1];
    return `rgb(${r},${g},${b})`;
  }
  const last = stops[stops.length - 1];
  if (temperature >= last[0]) {
    const [r, g, b] = last[1];
    return `rgb(${r},${g},${b})`;
  }
  for (let i = 0; i < stops.length - 1; i++) {
    const [t0, c0] = stops[i];
    const [t1, c1] = stops[i + 1];
    if (temperature >= t0 && temperature <= t1) {
      const ratio = (temperature - t0) / (t1 - t0);
      const r = Math.round(c0[0] + ratio * (c1[0] - c0[0]));
      const g = Math.round(c0[1] + ratio * (c1[1] - c0[1]));
      const b = Math.round(c0[2] + ratio * (c1[2] - c0[2]));
      return `rgb(${r},${g},${b})`;
    }
  }
  const [r, g, b] = last[1];
  return `rgb(${r},${g},${b})`;
}

function getSmokeLevel(temperature) {
  if (temperature < MAILLARD_TEMP_START) return 'none';
  if (temperature < FIRST_CRACK_TEMP) return 'faint';
  if (temperature < BURN_THRESHOLD_TEMP) return 'moderate';
  return 'heavy';
}

function getGrainStateName(temperature) {
  if (temperature <= 130) return 'DRYING';
  if (temperature <= 179) return 'MAILLARD';
  if (temperature <= 180) return 'FIRST_CRACK';
  if (temperature <= 199) return 'DARK';
  return 'BURNED';
}

function formatTime(totalSeconds) {
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

function calculateQualityScore(finalSim) {
  const {
    finalTemperature,
    burnedSeconds,
    firstCrackReached,
    firstCrackTimeSeconds,
    roastingElapsedSeconds,
    maillardStagnationFlag,
  } = finalSim;

  if (!firstCrackReached || finalTemperature < 175) {
    const rawRatio = Math.max(0, (finalTemperature - AMBIENT_TEMP) / (175 - AMBIENT_TEMP));
    return { score: Math.round(rawRatio * 38), result: 'RAW' };
  }

  if (burnedSeconds > BURN_ALERT_SECONDS || finalTemperature > 195) {
    const extra = Math.max(0, burnedSeconds - BURN_ALERT_SECONDS) * 0.4;
    return { score: Math.max(5, Math.round(18 - extra)), result: 'BURNED' };
  }

  if (maillardStagnationFlag) {
    return { score: 45, result: 'BAKED' };
  }

  const developmentSeconds = roastingElapsedSeconds - (firstCrackTimeSeconds || 0);
  const totalMinutes = roastingElapsedSeconds / 60;
  const developmentMinutes = developmentSeconds / 60;

  const tempPenalty = Math.abs(finalTemperature - 182.5) * 2.5;
  const timePenalty = Math.abs(totalMinutes - 7) * 4;
  const devPenalty = Math.abs(developmentMinutes - 1.5) * 8;

  return {
    score: Math.max(51, Math.min(100, Math.round(100 - tempPenalty - timePenalty - devPenalty))),
    result: 'PERFECT',
  };
}

function createInitialSimState() {
  return {
    phase: PHASES.IDLE,
    temperature: AMBIENT_TEMP,
    targetTemperature: TARGET_TEMP_DEFAULT,
    elapsedSeconds: 0,
    roastingElapsedSeconds: 0,
    burnerPower: POWER.MEDIUM,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    burnedSeconds: 0,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
    prevTemperature: AMBIENT_TEMP,
    peakTemperature: AMBIENT_TEMP,
    finalTemperature: null,
    chartData: [],
  };
}

// ================================================================
// TEMPERATURE CHART (custom SVG — no external dependencies)
// ================================================================

function TemperatureChart({ data, texts }) {
  if (!data || data.length < 2) return null;

  const VIEW_W = 820;
  const VIEW_H = 230;
  const PAD = { top: 15, right: 72, bottom: 48, left: 50 };
  const plotW = VIEW_W - PAD.left - PAD.right;
  const plotH = VIEW_H - PAD.top - PAD.bottom;

  const maxTime = Math.max(data[data.length - 1].time, 120);
  const maxTemp = 240;

  const xScale = (t) => (t / maxTime) * plotW;
  const yScale = (temp) => plotH - (temp / maxTemp) * plotH;

  const pathD = data.reduce((acc, pt, i) => {
    const x = xScale(pt.time).toFixed(1);
    const y = yScale(pt.temp).toFixed(1);
    return acc + `${i === 0 ? 'M' : 'L'}${x},${y} `;
  }, '');

  const yGridLines = [0, 50, 100, 150, 200];
  const tickCount = Math.min(8, Math.floor(maxTime / 30) + 1);
  const timeStep = Math.ceil(maxTime / tickCount);
  const timeTicks = Array.from({ length: tickCount + 1 }, (_, i) =>
    Math.min(maxTime, i * timeStep)
  );

  return (
    <div className="chart-wrapper">
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        width="100%"
        className="temperature-chart"
        aria-label={texts.chart.title}
      >
        <g transform={`translate(${PAD.left},${PAD.top})`}>
          {yGridLines.map((temp) => (
            <g key={temp}>
              <line
                x1={0} y1={yScale(temp)} x2={plotW} y2={yScale(temp)}
                stroke="var(--color-border)" strokeDasharray="4 3" strokeWidth={1}
              />
              <text
                x={-8} y={yScale(temp) + 4}
                fontSize={11} fill="var(--color-ink-muted)" textAnchor="end"
              >
                {temp}
              </text>
            </g>
          ))}

          {/* First crack reference (180°C) */}
          <line
            x1={0} y1={yScale(180)} x2={plotW} y2={yScale(180)}
            stroke="rgba(123,63,0,0.75)" strokeDasharray="6 3" strokeWidth={1.5}
          />
          <text x={plotW + 5} y={yScale(180) + 4} fontSize={10} fill="rgba(123,63,0,0.9)">
            {texts.chart.referenceLine180}
          </text>

          {/* Burn zone reference (200°C) */}
          <line
            x1={0} y1={yScale(200)} x2={plotW} y2={yScale(200)}
            stroke="var(--color-danger)" strokeDasharray="6 3" strokeWidth={1.5} opacity={0.65}
          />
          <text x={plotW + 5} y={yScale(200) + 4} fontSize={10} fill="var(--color-danger)">
            {texts.chart.referenceLine200}
          </text>

          {/* Data line */}
          <path d={pathD} fill="none" stroke="var(--color-accent)" strokeWidth={2.5} strokeLinejoin="round" />

          {/* X axis baseline */}
          <line x1={0} y1={plotH} x2={plotW} y2={plotH} stroke="var(--color-border)" />

          {timeTicks.map((t) => (
            <g key={t}>
              <line x1={xScale(t)} y1={plotH} x2={xScale(t)} y2={plotH + 5} stroke="var(--color-border)" />
              <text
                x={xScale(t).toFixed(1)} y={plotH + 18}
                fontSize={10} fill="var(--color-ink-muted)" textAnchor="middle"
              >
                {formatTime(t)}
              </text>
            </g>
          ))}

          {/* Axis labels */}
          <text
            transform={`translate(-36,${plotH / 2}) rotate(-90)`}
            fontSize={11} fill="var(--color-ink-muted)" textAnchor="middle"
          >
            {texts.chart.tempLabel}
          </text>
          <text
            x={plotW / 2} y={plotH + 38}
            fontSize={11} fill="var(--color-ink-muted)" textAnchor="middle"
          >
            {texts.chart.timeLabel}
          </text>
        </g>
      </svg>
    </div>
  );
}

// ================================================================
// MAIN COMPONENT
// ================================================================

export default function RoastingSimulation({ texts, currentUser, onLogout }) {
  const [simState, setSimState] = useState(createInitialSimState);
  const [targetTempInput, setTargetTempInput] = useState(TARGET_TEMP_DEFAULT);
  const [roastResult, setRoastResult] = useState(null);
  const [savingState, setSavingState] = useState('idle'); // idle | saving | saved | error
  const [showCrackAlert, setShowCrackAlert] = useState(false);

  const intervalRef = useRef(null);
  const crackFiredRef = useRef(false);
  // Always holds the latest simState for use in effects and event handlers
  const latestSimRef = useRef(simState);

  useEffect(() => {
    latestSimRef.current = simState;
  }, [simState]);

  const stopInterval = useCallback(() => {
    clearInterval(intervalRef.current);
    intervalRef.current = null;
  }, []);

  // Simulation tick — uses functional setState to avoid stale closures
  const runTick = useCallback(() => {
    setSimState((prev) => {
      if (prev.phase === PHASES.PREHEAT) {
        const newTemp = Math.min(
          prev.targetTemperature,
          prev.temperature + PREHEAT_RATE_PER_SECOND
        );
        const newElapsed = prev.elapsedSeconds + 1;
        return {
          ...prev,
          temperature: parseFloat(newTemp.toFixed(1)),
          elapsedSeconds: newElapsed,
          chartData: [...prev.chartData, { time: newElapsed, temp: parseFloat(newTemp.toFixed(1)) }],
          phase: newTemp >= prev.targetTemperature ? PHASES.READY : PHASES.PREHEAT,
        };
      }

      if (prev.phase === PHASES.COOLDOWN) {
        const coolRate = COOLDOWN_TOTAL_DROP / COOLDOWN_DURATION_SECONDS;
        const newTemp = parseFloat(
          Math.max(prev.targetTemperature - COOLDOWN_TOTAL_DROP, prev.temperature - coolRate).toFixed(1)
        );
        const newRoastingTime = prev.roastingElapsedSeconds + 1;
        return {
          ...prev,
          temperature: newTemp,
          elapsedSeconds: prev.elapsedSeconds + 1,
          roastingElapsedSeconds: newRoastingTime,
          chartData: [...prev.chartData, { time: newRoastingTime, temp: newTemp }],
          phase: newRoastingTime >= COOLDOWN_DURATION_SECONDS ? PHASES.ROASTING : PHASES.COOLDOWN,
        };
      }

      if (prev.phase === PHASES.ROASTING) {
        const rate = TEMP_RATE_PER_SECOND[prev.burnerPower];
        const newTemp = parseFloat(
          Math.max(AMBIENT_TEMP, Math.min(MAX_SAFE_TEMP, prev.temperature + rate)).toFixed(1)
        );
        const newRoastingTime = prev.roastingElapsedSeconds + 1;

        const newBurnedSeconds =
          newTemp >= BURN_THRESHOLD_TEMP ? prev.burnedSeconds + 1 : prev.burnedSeconds;

        const inMaillard = newTemp >= MAILLARD_TEMP_START && newTemp <= MAILLARD_TEMP_END;
        const tempDropped = newTemp <= prev.prevTemperature;
        let newStagnationSecs = prev.maillardStagnationSeconds;
        let newStagnationFlag = prev.maillardStagnationFlag;
        if (inMaillard) {
          if (tempDropped) {
            newStagnationSecs += 1;
            if (newStagnationSecs >= MAILLARD_STAGNATION_LIMIT) newStagnationFlag = true;
          } else {
            newStagnationSecs = 0;
          }
        }

        const hitFirstCrack = !prev.firstCrackReached && newTemp >= FIRST_CRACK_TEMP;

        return {
          ...prev,
          temperature: newTemp,
          prevTemperature: prev.temperature,
          elapsedSeconds: prev.elapsedSeconds + 1,
          roastingElapsedSeconds: newRoastingTime,
          peakTemperature: Math.max(prev.peakTemperature, newTemp),
          burnedSeconds: newBurnedSeconds,
          maillardStagnationSeconds: newStagnationSecs,
          maillardStagnationFlag: newStagnationFlag,
          firstCrackReached: prev.firstCrackReached || hitFirstCrack,
          firstCrackTimeSeconds: hitFirstCrack ? newRoastingTime : prev.firstCrackTimeSeconds,
          chartData: [...prev.chartData, { time: newRoastingTime, temp: newTemp }],
        };
      }

      return prev;
    });
  }, []);

  // Stop interval when preheat completes
  useEffect(() => {
    if (simState.phase === PHASES.READY) {
      stopInterval();
    }
  }, [simState.phase, stopInterval]);

  // Trigger first crack sound and alert
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

  // Cleanup on unmount
  useEffect(() => () => stopInterval(), [stopInterval]);

  // ── Event handlers ──────────────────────────────────────────────

  const handleStartPreheat = () => {
    crackFiredRef.current = false;
    setRoastResult(null);
    setSavingState('idle');
    setSimState({
      ...createInitialSimState(),
      phase: PHASES.PREHEAT,
      targetTemperature: targetTempInput,
    });
    intervalRef.current = setInterval(runTick, 1000);
  };

  const handleLoadBeans = () => {
    setSimState((prev) => ({ ...prev, phase: PHASES.COOLDOWN }));
    intervalRef.current = setInterval(runTick, 1000);
  };

  const handleChangePower = useCallback((power) => {
    setSimState((prev) => ({ ...prev, burnerPower: power }));
  }, []);

  const handleDischarge = useCallback(async () => {
    stopInterval();
    const current = latestSimRef.current;
    const finalSim = {
      ...current,
      phase: PHASES.FINISHED,
      finalTemperature: current.temperature,
    };
    const scoreResult = calculateQualityScore(finalSim);

    setSimState(finalSim);
    setRoastResult(scoreResult);
    setSavingState('saving');

    try {
      await saveRoastingSession({
        targetTemperature: finalSim.targetTemperature,
        totalDurationSeconds: finalSim.roastingElapsedSeconds,
        finalTemperature: finalSim.finalTemperature,
        peakTemperature: finalSim.peakTemperature,
        result: scoreResult.result,
        qualityScore: scoreResult.score,
        firstCrackReached: finalSim.firstCrackReached,
        developmentTimeSeconds: finalSim.firstCrackReached
          ? finalSim.roastingElapsedSeconds - (finalSim.firstCrackTimeSeconds || 0)
          : 0,
      });
      setSavingState('saved');
    } catch (_) {
      setSavingState('error');
    }
  }, [stopInterval]);

  const handleNewSimulation = () => {
    crackFiredRef.current = false;
    stopInterval();
    setSimState(createInitialSimState());
    setRoastResult(null);
    setSavingState('idle');
    setTargetTempInput(TARGET_TEMP_DEFAULT);
  };

  // ── Derived render values ────────────────────────────────────────

  const s = simState;
  const grainColor = interpolateGrainColor(s.temperature);
  const smokeLevel = getSmokeLevel(s.temperature);
  const isBlackSmoke = s.temperature >= BURN_THRESHOLD_TEMP;
  const smokeColor = isBlackSmoke ? 'rgba(40,40,40,0.8)' : 'rgba(210,210,210,0.75)';
  const showBeans =
    s.phase === PHASES.COOLDOWN ||
    s.phase === PHASES.ROASTING ||
    s.phase === PHASES.FINISHED;
  const showSmoke = showBeans && smokeLevel !== 'none';
  const ovenGlowOpacity = Math.min(0.6, Math.max(0, (s.temperature - 50) / 200));
  const thermFillPct = Math.max(2, (s.temperature / MAX_SAFE_TEMP) * 100);
  const thermColor = s.temperature >= BURN_THRESHOLD_TEMP ? 'var(--color-danger)' : 'var(--color-accent)';

  const sliderPct =
    ((targetTempInput - TARGET_TEMP_MIN) / (TARGET_TEMP_MAX - TARGET_TEMP_MIN)) * 100;

  return (
    <div className="sim-layout">

      {/* ── Header ─────────────────────────────────────────── */}
      <header className="sim-header">
        <div className="sim-header-brand">
          <span className="sim-brand-name">{texts.title.split(' ').slice(0, 2).join(' ')}</span>
        </div>

        <div className="sim-header-status">
          <span className="sim-phase-badge">{texts.phases[s.phase]}</span>
          {s.phase !== PHASES.IDLE && (
            <span className="sim-temp-display">{s.temperature.toFixed(1)}°C</span>
          )}
          {(s.phase === PHASES.ROASTING || s.phase === PHASES.COOLDOWN || s.phase === PHASES.FINISHED) && (
            <span className="sim-time-display">
              {texts.labels.roastingTime}: {formatTime(s.roastingElapsedSeconds)}
            </span>
          )}
        </div>

        <div className="sim-header-user">
          <span className="sim-username-label">{currentUser.username}</span>
          <button type="button" className="secondary-button sim-logout-btn" onClick={onLogout}>
            {texts.buttons.logout}
          </button>
        </div>
      </header>

      {/* ── Alerts ─────────────────────────────────────────── */}
      {showCrackAlert && (
        <div className="sim-crack-alert" role="alert" aria-live="assertive">
          {texts.labels.firstCrack}
        </div>
      )}
      {s.phase === PHASES.ROASTING && s.temperature >= BURN_THRESHOLD_TEMP && (
        <div className="sim-burn-alert" role="alert" aria-live="polite">
          {texts.labels.burnAlert}
        </div>
      )}

      {/* ── Main grid: oven + controls ─────────────────────── */}
      <div className="sim-main-grid">

        {/* Left: Oven visualization */}
        <div className="sim-oven-col">
          <span className="sim-section-label">{texts.labels.grainState}</span>

          <div className="sim-oven-wrapper">
            <div
              className={`oven-body${s.phase !== PHASES.IDLE ? ' oven-active' : ''}`}
              style={{ '--oven-glow': `rgba(185,100,30,${ovenGlowOpacity})` }}
            >
              <div className="oven-window">
                {s.phase !== PHASES.IDLE && (
                  <div
                    className="oven-heat-glow"
                    style={{ opacity: ovenGlowOpacity }}
                  />
                )}

                {showBeans && (
                  <div className="beans-cluster">
                    {[0, 1, 2, 3, 4, 5, 6].map((i) => (
                      <div
                        key={i}
                        className={`coffee-bean bean-pos-${i}`}
                        style={{ backgroundColor: grainColor }}
                      />
                    ))}
                  </div>
                )}

                {showSmoke && (
                  <div className={`smoke-container smoke-${smokeLevel}`}>
                    {[0, 1, 2].map((i) => (
                      <div
                        key={i}
                        className="smoke-puff"
                        style={{ '--smoke-color': smokeColor }}
                      />
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Thermometer */}
            <div className="sim-thermometer">
              <div className="therm-track">
                <div
                  className="therm-fill"
                  style={{ height: `${thermFillPct}%`, backgroundColor: thermColor }}
                />
              </div>
              <span className="therm-label">{s.temperature.toFixed(0)}°C</span>
            </div>
          </div>

          {showBeans && (
            <span className="sim-grain-state">{texts.grainStates[getGrainStateName(s.temperature)]}</span>
          )}
        </div>

        {/* Right: Control panel */}
        <div className="sim-controls-col">

          {/* IDLE — target temperature setup */}
          {s.phase === PHASES.IDLE && (
            <div className="control-section">
              <div className="control-section-title">{texts.labels.targetTemperature}</div>
              <div className="target-temp-control">
                <span className="temp-range-label">{TARGET_TEMP_MIN}°C</span>
                <input
                  type="range"
                  className="temp-slider"
                  min={TARGET_TEMP_MIN}
                  max={TARGET_TEMP_MAX}
                  step={5}
                  value={targetTempInput}
                  style={{ '--slider-pct': `${sliderPct}%` }}
                  onChange={(e) => setTargetTempInput(Number(e.target.value))}
                />
                <span className="temp-range-label">{TARGET_TEMP_MAX}°C</span>
              </div>
              <div className="target-temp-display">{targetTempInput}°C</div>
              <button
                type="button"
                className="primary-button sim-action-btn"
                onClick={handleStartPreheat}
              >
                {texts.buttons.startPreheat}
              </button>
            </div>
          )}

          {/* PREHEAT — progress bar */}
          {s.phase === PHASES.PREHEAT && (
            <div className="control-section">
              <div className="control-section-title">{texts.phases.PREHEAT}</div>
              <div className="preheat-progress">
                <div className="preheat-progress-track">
                  <div
                    className="preheat-progress-fill"
                    style={{
                      width: `${((s.temperature - AMBIENT_TEMP) / (s.targetTemperature - AMBIENT_TEMP)) * 100}%`,
                    }}
                  />
                </div>
                <div className="preheat-temps">
                  <span>{s.temperature.toFixed(0)}°C</span>
                  <span>/ {s.targetTemperature}°C</span>
                </div>
              </div>
            </div>
          )}

          {/* READY — load beans */}
          {s.phase === PHASES.READY && (
            <div className="control-section">
              <div className="control-section-title ready-badge">{texts.phases.READY}</div>
              <button
                type="button"
                className="primary-button sim-action-btn"
                onClick={handleLoadBeans}
              >
                {texts.buttons.loadBeans}
              </button>
            </div>
          )}

          {/* COOLDOWN — countdown display */}
          {s.phase === PHASES.COOLDOWN && (
            <div className="control-section">
              <div className="control-section-title">{texts.phases.COOLDOWN}</div>
              <div className="sim-muted-info">
                {texts.info.cooldownCountdown}: {formatTime(Math.max(0, COOLDOWN_DURATION_SECONDS - s.roastingElapsedSeconds))}
              </div>
            </div>
          )}

          {/* ROASTING — burner power + discharge */}
          {s.phase === PHASES.ROASTING && (
            <>
              <div className="control-section">
                <div className="control-section-title">{texts.labels.burnerPower}</div>
                <div className="power-buttons">
                  {Object.values(POWER).map((power) => (
                    <button
                      key={power}
                      type="button"
                      className={`power-btn${s.burnerPower === power ? ' power-btn-active' : ''}`}
                      onClick={() => handleChangePower(power)}
                    >
                      {texts.power[power]}
                    </button>
                  ))}
                </div>
              </div>

              <div className="control-section">
                <button
                  type="button"
                  className="primary-button sim-action-btn"
                  onClick={handleDischarge}
                >
                  {texts.buttons.dischargeCoffee}
                </button>
              </div>
            </>
          )}

          {/* Live stats (cooldown + roasting) */}
          {(s.phase === PHASES.COOLDOWN || s.phase === PHASES.ROASTING) && (
            <div className="sim-stats-mini">
              <div className="stat-item">
                <span className="stat-label">{texts.labels.elapsedTime}</span>
                <span className="stat-value">{formatTime(s.elapsedSeconds)}</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">{texts.labels.roastingTime}</span>
                <span className="stat-value">{formatTime(s.roastingElapsedSeconds)}</span>
              </div>
              {s.firstCrackReached && (
                <div className="stat-item stat-highlight">
                  <span className="stat-label">{texts.labels.firstCrack}</span>
                  <span className="stat-value">{formatTime(s.firstCrackTimeSeconds)}</span>
                </div>
              )}
              {s.firstCrackReached && (
                <div className="stat-item stat-highlight">
                  <span className="stat-label">{texts.info.developmentTime}</span>
                  <span className="stat-value">
                    {formatTime(s.roastingElapsedSeconds - (s.firstCrackTimeSeconds || 0))}
                  </span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── Temperature chart ───────────────────────────────── */}
      {s.chartData.length > 1 && (
        <div className="sim-chart-section">
          <h3 className="sim-section-title">{texts.chart.title}</h3>
          <TemperatureChart data={s.chartData} texts={texts} />
        </div>
      )}

      {/* ── Results ─────────────────────────────────────────── */}
      {s.phase === PHASES.FINISHED && roastResult && (
        <div className="sim-results-section">
          <h3 className="sim-section-title">{texts.results.title}</h3>

          <div className="results-grid">
            {/* Bean visual */}
            <div className="results-visual">
              <div className="results-bean" style={{ backgroundColor: grainColor }} />
              {showSmoke && (
                <div className={`smoke-container smoke-${smokeLevel} smoke-results`}>
                  {[0, 1, 2].map((i) => (
                    <div
                      key={i}
                      className="smoke-puff"
                      style={{ '--smoke-color': smokeColor }}
                    />
                  ))}
                </div>
              )}
            </div>

            {/* Score + details */}
            <div className="results-score-col">
              <div className="score-display">
                <div className="score-bar-track">
                  <div
                    className="score-bar-fill"
                    style={{ width: `${roastResult.score}%` }}
                  />
                </div>
                <span className="score-number">{roastResult.score}%</span>
              </div>

              <div className={`result-badge result-badge-${roastResult.result}`}>
                {texts.results[roastResult.result]}
              </div>

              <p className="result-description">
                {roastResult.score === 100
                  ? texts.results.descriptions.PERFECT_100
                  : roastResult.result === 'PERFECT'
                    ? texts.results.descriptions.PERFECT_GOOD
                    : texts.results.descriptions[roastResult.result]}
              </p>

              <div className="results-stats">
                <div className="stat-row">
                  <span>{texts.results.stats.finalTemp}</span>
                  <span>{s.finalTemperature?.toFixed(1)}°C</span>
                </div>
                <div className="stat-row">
                  <span>{texts.results.stats.totalTime}</span>
                  <span>{formatTime(s.roastingElapsedSeconds)}</span>
                </div>
                <div className="stat-row">
                  <span>{texts.results.stats.firstCrack}</span>
                  <span>
                    {s.firstCrackReached ? texts.results.stats.yes : texts.results.stats.no}
                  </span>
                </div>
                {s.firstCrackReached && s.firstCrackTimeSeconds != null && (
                  <div className="stat-row">
                    <span>{texts.results.stats.devTime}</span>
                    <span>
                      {formatTime(s.roastingElapsedSeconds - s.firstCrackTimeSeconds)}
                    </span>
                  </div>
                )}
              </div>

              {savingState === 'saving' && (
                <p className="sim-saving-text">{texts.results.saving}</p>
              )}
              {savingState === 'saved' && (
                <p className="sim-saved-text">{texts.results.saved}</p>
              )}
              {savingState === 'error' && (
                <p className="sim-save-error-text">{texts.results.saveError}</p>
              )}

              <button
                type="button"
                className="primary-button sim-action-btn"
                onClick={handleNewSimulation}
                style={{ marginTop: '8px' }}
              >
                {texts.buttons.newSimulation}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
