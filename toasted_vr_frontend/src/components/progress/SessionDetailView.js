import React, { useEffect, useState } from 'react';
import { getSessionDetail } from '../../services/progressService';
import { getRoastingFeedback } from '../../services/simulationService';
import { formatLocalDateTime } from '../../utils/dateFormat';
import { RESULT_PILL_CLASSES, formatLevel, formatMinutes, formatRatio, formatTemperature } from './progressFormat';

// Detalle de una sesión del historial, con la opción de volver a pedir la
// retroalimentación de IA (usa el mismo endpoint que la pantalla de resultados).
export default function SessionDetailView({ texts, sessionId, onBack }) {
  const [session, setSession] = useState(null);
  const [status, setStatus] = useState('loading');
  const [feedbackState, setFeedbackState] = useState('idle');
  const [feedbackText, setFeedbackText] = useState('');

  useEffect(() => {
    getSessionDetail(sessionId)
      .then((data) => {
        setSession(data);
        setStatus('ready');
      })
      .catch(() => setStatus('error'));
  }, [sessionId]);

  const handleRequestFeedback = async () => {
    setFeedbackState('loading');
    try {
      const feedback = await getRoastingFeedback(sessionId);
      setFeedbackText(feedback);
      setFeedbackState(feedback ? 'ready' : 'unavailable');
    } catch (error) {
      setFeedbackState('unavailable');
    }
  };

  const backButton = (
    <button type="button" className="secondary-button" onClick={onBack}>
      {texts.buttons.back}
    </button>
  );

  if (status === 'loading') return <p className="empty-state">{texts.detail.loading}</p>;
  if (status === 'error') {
    return (
      <>
        <p className="sim-save-error-text">{texts.loadError}</p>
        {backButton}
      </>
    );
  }

  const rows = [
    [texts.columns.date, formatLocalDateTime(session.createdAt)],
    [texts.columns.level, formatLevel(session.knowledgeLevel, texts)],
    [texts.columns.score, `${session.qualityScore}%`],
    [texts.detail.chargeTemp, formatTemperature(session.chargeTemperature, texts)],
    [texts.detail.targetTemp, formatTemperature(session.targetTemperature, texts)],
    [texts.columns.finalTemp, formatTemperature(session.finalTemperature, texts)],
    [texts.detail.peakTemp, formatTemperature(session.peakTemperature, texts)],
    [texts.columns.duration, formatMinutes(session.totalDurationSeconds, texts)],
    [texts.detail.firstCrack, session.firstCrackReached ? texts.detail.yes : texts.detail.no],
    [texts.detail.devTime, formatMinutes(session.developmentTimeSeconds, texts)],
    [texts.columns.dtr, formatRatio(session.developmentTimeRatio, texts)],
  ];

  return (
    <>
      <h3>{texts.detail.title}</h3>
      <span className={`result-pill ${RESULT_PILL_CLASSES[session.result] || ''}`}>
        {texts.results[session.result] || session.result}
      </span>

      <div className="results-stats">
        {rows.map(([label, value]) => (
          <div className="stat-row" key={label}>
            <span>{label}</span>
            <span>{value}</span>
          </div>
        ))}
      </div>

      {feedbackState === 'idle' && (
        <button type="button" className="primary-button" onClick={handleRequestFeedback}>
          {texts.buttons.feedback}
        </button>
      )}
      {feedbackState !== 'idle' && (
        <div className="sim-ai-feedback">
          <h3 className="sim-ai-feedback-title">{texts.feedback.title}</h3>
          {feedbackState === 'loading' && <p className="sim-feedback-loading-text">{texts.feedback.loading}</p>}
          {feedbackState === 'ready' && <p className="sim-feedback-text">{feedbackText}</p>}
          {feedbackState === 'unavailable' && (
            <p className="sim-feedback-unavailable-text">{texts.feedback.unavailable}</p>
          )}
        </div>
      )}

      {backButton}
    </>
  );
}
