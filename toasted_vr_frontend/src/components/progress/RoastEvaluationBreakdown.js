import React from 'react';
import { formatGrade, scoreOf } from './progressFormat';

const fill = (template, values) =>
  Object.entries(values).reduce((text, [key, value]) => text.replace(`{${key}}`, value), template);

const percent = (weight) => Math.round(weight * 100);

// Calificación combinada de una sesión: la nota final con su calificación, cuánto pesó cada parte
// y lo que dijo la IA de cada criterio. Si la IA no calificó, explica por qué vale la de las reglas.
export default function RoastEvaluationBreakdown({ texts, session, timedOut }) {
  const evaluationTexts = texts.evaluation;
  const evaluation = session.evaluation;
  const status = evaluation?.status;
  const scoreLine = fill(evaluationTexts.scoreWithGrade, {
    score: scoreOf(session),
    grade: formatGrade(session.grade, texts),
  });

  return (
    <div className="result-breakdown">
      <span className="result-breakdown-title">{evaluationTexts.title}</span>
      <div className="results-stats">
        <div className="stat-row">
          <span>{evaluationTexts.finalScore}</span>
          <span>{scoreLine}</span>
        </div>
        {status === 'COMPLETED' && (
          <>
            <div className="stat-row">
              <span>{fill(evaluationTexts.rulesScore, { weight: percent(evaluation.rulesWeight) })}</span>
              <span>{evaluation.rulesScore}%</span>
            </div>
            <div className="stat-row">
              <span>{fill(evaluationTexts.rubricScore, { weight: percent(evaluation.aiWeight) })}</span>
              <span>{evaluation.rubricScore}%</span>
            </div>
          </>
        )}
      </div>

      {status === 'COMPLETED' && (
        <>
          {evaluation.defectCapApplied && <p className="sim-feedback-text">{evaluationTexts.defectCap}</p>}
          <span className="result-breakdown-title">{evaluationTexts.criteriaTitle}</span>
          <ul className="result-breakdown-list">
            {evaluation.criteria.map((criterion) => (
              <li key={criterion.id}>
                <strong>
                  {fill(evaluationTexts.criterionLevel, {
                    title: evaluationTexts.criteria[criterion.id] || criterion.id,
                    level: criterion.level,
                    max: criterion.maxLevel,
                  })}
                </strong>{' '}
                {criterion.justification}
              </li>
            ))}
          </ul>
          {evaluation.notApplicable.length > 0 && (
            <p className="sim-feedback-unavailable-text">
              {fill(evaluationTexts.notApplicable, {
                criteria: evaluation.notApplicable.map((id) => evaluationTexts.criteria[id] || id).join(', '),
              })}
            </p>
          )}
        </>
      )}
      {status === 'PENDING' && (
        <p className="sim-feedback-loading-text">{timedOut ? evaluationTexts.slow : evaluationTexts.pending}</p>
      )}
      {status === 'FAILED' && <p className="sim-feedback-unavailable-text">{evaluationTexts.failed}</p>}
      {status === 'NOT_APPLICABLE' && (
        <p className="sim-feedback-unavailable-text">
          {evaluationTexts.skipReasons[evaluation.skipReason] || evaluationTexts.failed}
        </p>
      )}
      {!evaluation && <p className="sim-feedback-unavailable-text">{evaluationTexts.legacy}</p>}
    </div>
  );
}
