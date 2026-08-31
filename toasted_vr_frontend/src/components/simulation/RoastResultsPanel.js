import React from 'react';
import GrainAppearanceModel from '../../domain/roasting/GrainAppearanceModel';
import RoastFlavorProfileDescriber from '../../domain/roasting/RoastFlavorProfileDescriber';
import RoastMetrics from '../../domain/roasting/RoastMetrics';
import TemperatureUnitConverter from '../../domain/roasting/TemperatureUnitConverter';

// ================================================================
// RoastResultsPanel
// Responsabilidad única: mostrar la evaluación final del tueste —
// grano, barra de puntuación, insignia de resultado y estadísticas.
// ================================================================
export default function RoastResultsPanel({
  sim,
  roastResult,
  savingState,
  saveErrorDetail,
  feedbackState,
  feedbackText,
  temperatureUnit,
  texts,
  onNewSimulation,
}) {
  const grainColor = GrainAppearanceModel.interpolateColor(sim.finalTemperature ?? sim.temperature);
  const smokeLevel = GrainAppearanceModel.getSmokeLevel(sim.finalTemperature ?? sim.temperature);
  const isBurntSmoke = GrainAppearanceModel.isBurntSmoke(sim.finalTemperature ?? sim.temperature);
  const showSmoke = smokeLevel !== 'none';
  const descriptionKey =
    roastResult.score === 100 ? 'PERFECT_100' : RoastFlavorProfileDescriber.describe({ ...sim, result: roastResult.result });
  const breakdownLines = roastResult.breakdown
    ? Object.entries(roastResult.breakdown)
        .filter(([, points]) => points > 0)
        .map(([key, points]) => texts.breakdown[key].replace('{points}', points))
    : [];

  return (
    <div className="sim-results-section">
      <h3 className="sim-section-title">{texts.title}</h3>

      <div className="results-grid">
        <div className="results-visual">
          <div className="results-bean" style={{ backgroundColor: grainColor }} />
          {showSmoke && (
            <div className={`smoke-container smoke-${smokeLevel} smoke-results${isBurntSmoke ? ' smoke-burnt' : ''}`}>
              {[0, 1, 2].map((i) => (
                <div key={i} className="smoke-puff" />
              ))}
            </div>
          )}
        </div>

        <div className="results-score-col">
          <div className="score-display">
            <div className="score-bar-track">
              <div className="score-bar-fill" style={{ width: `${roastResult.score}%` }} />
            </div>
            <span className="score-number">{roastResult.score}%</span>
          </div>

          <div className={`result-badge result-badge-${roastResult.result}`}>
            {texts[roastResult.result]}
          </div>

          <p className="result-description">{texts.descriptions[descriptionKey]}</p>

          {breakdownLines.length > 0 && (
            <div className="result-breakdown">
              <span className="result-breakdown-title">{texts.breakdown.title}</span>
              <ul className="result-breakdown-list">
                {breakdownLines.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </div>
          )}

          <div className="results-stats">
            <div className="stat-row">
              <span>{texts.stats.finalTemp}</span>
              <span>
                {sim.finalTemperature != null
                  ? Math.round(TemperatureUnitConverter.toDisplay(sim.finalTemperature, temperatureUnit))
                  : '—'}
                {TemperatureUnitConverter.unitSymbol(temperatureUnit)}
              </span>
            </div>
            <div className="stat-row">
              <span>{texts.stats.totalTime}</span>
              <span>{RoastMetrics.formatDecimalMinutes(sim.roastingElapsedSeconds)} {texts.stats.minutesUnit}</span>
            </div>
            <div className="stat-row">
              <span>{texts.stats.firstCrack}</span>
              <span>{sim.firstCrackReached ? texts.stats.yes : texts.stats.no}</span>
            </div>
            {sim.firstCrackReached && sim.firstCrackTimeSeconds != null && (
              <div className="stat-row">
                <span>{texts.stats.devTime}</span>
                <span>
                  {RoastMetrics.formatDecimalMinutes(sim.roastingElapsedSeconds - sim.firstCrackTimeSeconds)} {texts.stats.minutesUnit}
                </span>
              </div>
            )}
          </div>

          {savingState === 'saving' && <p className="sim-saving-text">{texts.saving}</p>}
          {savingState === 'saved' && <p className="sim-saved-text">{texts.saved}</p>}
          {savingState === 'error' && (
            <p className="sim-save-error-text">
              {texts.saveError}
              {saveErrorDetail ? ` (${saveErrorDetail})` : ''}
            </p>
          )}

          {(feedbackState === 'loading' || feedbackState === 'ready' || feedbackState === 'unavailable') && (
            <div className="sim-ai-feedback">
              <h4 className="sim-ai-feedback-title">{texts.feedback.title}</h4>
              {feedbackState === 'loading' && <p className="sim-feedback-loading-text">{texts.feedback.loading}</p>}
              {feedbackState === 'ready' && <p className="sim-feedback-text">{feedbackText}</p>}
              {feedbackState === 'unavailable' && (
                <p className="sim-feedback-unavailable-text">{texts.feedback.unavailable}</p>
              )}
            </div>
          )}

          <button
            type="button"
            className="primary-button sim-action-btn"
            onClick={onNewSimulation}
            style={{ marginTop: '8px' }}
          >
            {texts.newSimulationButton}
          </button>
        </div>
      </div>
    </div>
  );
}
