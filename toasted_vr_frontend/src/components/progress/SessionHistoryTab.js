import React, { useEffect, useState } from 'react';
import { getSessionHistory, getSessionSummary } from '../../services/progressService';
import { formatLocalDateTime } from '../../utils/dateFormat';
import { RESULT_PILL_CLASSES, formatLevel, formatMinutes, formatRatio, formatTemperature } from './progressFormat';

const RESULTS = ['PERFECT', 'RAW', 'BURNED', 'BAKED'];
const emptyPage = { content: [], number: 0, totalPages: 0, totalElements: 0 };

// Historial del jugador (RF016): resumen, filtro por resultado y tabla
// paginada; el servidor decide cuántas sesiones trae cada página.
export default function SessionHistoryTab({ texts, onSelectSession }) {
  const [summary, setSummary] = useState(null);
  const [historyPage, setHistoryPage] = useState(emptyPage);
  const [pageNumber, setPageNumber] = useState(0);
  const [resultFilter, setResultFilter] = useState('');
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    getSessionSummary()
      .then(setSummary)
      .catch(() => setSummary(null));
  }, []);

  useEffect(() => {
    let isCurrent = true;
    setStatus('loading');
    getSessionHistory({ page: pageNumber, result: resultFilter })
      .then((data) => {
        if (!isCurrent) return;
        setHistoryPage(data);
        setStatus('ready');
      })
      .catch(() => {
        if (isCurrent) setStatus('error');
      });
    // Evita que una respuesta vieja pise a la del filtro o página actual.
    return () => {
      isCurrent = false;
    };
  }, [pageNumber, resultFilter]);

  const handleFilterChange = (event) => {
    setResultFilter(event.target.value);
    setPageNumber(0);
  };

  const renderSummary = () => (
    <div className="results-stats">
      <div className="stat-row">
        <span>{texts.summary.best}</span>
        <span>{summary?.bestScore != null ? `${summary.bestScore}%` : texts.noValue}</span>
      </div>
      <div className="stat-row">
        <span>{texts.summary.average}</span>
        <span>{summary?.averageScore != null ? `${summary.averageScore}%` : texts.noValue}</span>
      </div>
      <div className="stat-row">
        <span>{texts.summary.total}</span>
        <span>{summary?.totalSessions ?? 0}</span>
      </div>
    </div>
  );

  const renderTable = () => (
    <>
      <div className="users-table-wrapper">
        <table className="admin-compact-table">
          <thead>
            <tr>
              <th>{texts.columns.date}</th>
              <th>{texts.columns.level}</th>
              <th>{texts.columns.result}</th>
              <th>{texts.columns.score}</th>
              <th>{texts.columns.finalTemp}</th>
              <th>{texts.columns.duration}</th>
              <th>{texts.columns.dtr}</th>
              <th aria-label={texts.buttons.view} />
            </tr>
          </thead>
          <tbody>
            {historyPage.content.map((session) => (
              <tr key={session.id}>
                <td className="admin-muted-cell">{formatLocalDateTime(session.createdAt)}</td>
                <td>{formatLevel(session.knowledgeLevel, texts)}</td>
                <td>
                  <span className={`result-pill ${RESULT_PILL_CLASSES[session.result] || ''}`}>
                    {texts.results[session.result] || session.result}
                  </span>
                </td>
                <td className="admin-score-cell">{session.qualityScore}%</td>
                <td>{formatTemperature(session.finalTemperature, texts)}</td>
                <td>{formatMinutes(session.totalDurationSeconds, texts)}</td>
                <td>{formatRatio(session.developmentTimeRatio, texts)}</td>
                <td>
                  <button type="button" className="admin-row-action-btn" onClick={() => onSelectSession(session.id)}>
                    {texts.buttons.view}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="pagination-row">
        <button
          type="button"
          className="secondary-button"
          onClick={() => setPageNumber((current) => current - 1)}
          disabled={historyPage.number === 0}
        >
          {texts.buttons.previous}
        </button>
        <span>
          {texts.pageLabel
            .replace('{page}', historyPage.totalPages === 0 ? 0 : historyPage.number + 1)
            .replace('{totalPages}', historyPage.totalPages)}
        </span>
        <button
          type="button"
          className="secondary-button"
          onClick={() => setPageNumber((current) => current + 1)}
          disabled={historyPage.number + 1 >= historyPage.totalPages}
        >
          {texts.buttons.next}
        </button>
      </div>
    </>
  );

  const renderBody = () => {
    if (status === 'loading') return <p className="empty-state">{texts.loading}</p>;
    if (status === 'error') return <p className="sim-save-error-text">{texts.loadError}</p>;
    if (historyPage.content.length === 0) {
      return <p className="empty-state">{resultFilter ? texts.emptyFiltered : texts.empty}</p>;
    }
    return renderTable();
  };

  return (
    <>
      {renderSummary()}

      <label className="field-group">
        <span className="field-label">{texts.filter.label}</span>
        <select className="field-input" value={resultFilter} onChange={handleFilterChange}>
          <option value="">{texts.filter.all}</option>
          {RESULTS.map((result) => (
            <option key={result} value={result}>
              {texts.results[result]}
            </option>
          ))}
        </select>
      </label>

      {renderBody()}
    </>
  );
}
