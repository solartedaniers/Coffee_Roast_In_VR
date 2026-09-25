import React, { useEffect, useState } from 'react';
import { getRanking } from '../../services/progressService';
import { formatLocalDate } from '../../utils/dateFormat';

const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

// Ranking por nivel (RF017). Primero se pide sin nivel para que el servidor
// use el del jugador; después, el que elija en el selector. Solo se muestran
// posición, username, mejor puntaje y fecha.
export default function RankingTab({ texts }) {
  const rankingTexts = texts.ranking;
  const [chosenLevel, setChosenLevel] = useState(null);
  const [ranking, setRanking] = useState(null);
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    let isCurrent = true;
    setStatus('loading');
    getRanking(chosenLevel)
      .then((data) => {
        if (!isCurrent) return;
        setRanking(data);
        setStatus('ready');
      })
      .catch(() => {
        if (isCurrent) setStatus('error');
      });
    return () => {
      isCurrent = false;
    };
  }, [chosenLevel]);

  const selectedLevel = chosenLevel ?? ranking?.level ?? '';
  const me = ranking?.me;
  const isMeOutsideTop = me != null && me.position > ranking.top.length;

  const renderRow = (entry, isMe) => (
    <tr key={entry.position} className={isMe ? 'is-selected' : undefined}>
      <td className="admin-score-cell">{entry.position}</td>
      <td>
        <strong>{entry.username}</strong>
        {isMe && ` ${rankingTexts.you}`}
      </td>
      <td className="admin-score-cell">{entry.bestScore}%</td>
      <td className="admin-muted-cell">{formatLocalDate(entry.achievedAt)}</td>
    </tr>
  );

  const renderTable = () => (
    <div className="users-table-wrapper">
      <table className="admin-compact-table">
        <thead>
          <tr>
            <th>{rankingTexts.columns.position}</th>
            <th>{rankingTexts.columns.username}</th>
            <th>{rankingTexts.columns.bestScore}</th>
            <th>{rankingTexts.columns.date}</th>
          </tr>
        </thead>
        <tbody>
          {ranking.top.map((entry) => renderRow(entry, me?.position === entry.position))}
          {isMeOutsideTop && (
            <>
              <tr aria-hidden="true">
                <td colSpan={4} className="admin-muted-cell">…</td>
              </tr>
              {renderRow(me, true)}
            </>
          )}
        </tbody>
      </table>
    </div>
  );

  const renderBody = () => {
    if (status === 'loading') return <p className="empty-state">{rankingTexts.loading}</p>;
    if (status === 'error') return <p className="sim-save-error-text">{rankingTexts.loadError}</p>;
    if (ranking.level == null) return <p className="empty-state">{rankingTexts.noLevel}</p>;

    return (
      <>
        {ranking.hasSessionsInLevel ? (
          <div className="results-stats">
            <div className="stat-row">
              <span>{rankingTexts.yourPosition}</span>
              <span>#{me.position}</span>
            </div>
          </div>
        ) : (
          <p className="sim-hint-text">{rankingTexts.noSessionsInLevel}</p>
        )}
        {ranking.top.length === 0 ? <p className="empty-state">{rankingTexts.empty}</p> : renderTable()}
      </>
    );
  };

  return (
    <>
      <label className="field-group">
        <span className="field-label">{rankingTexts.levelLabel}</span>
        <select
          className="field-input"
          value={selectedLevel}
          onChange={(event) => setChosenLevel(event.target.value)}
        >
          {selectedLevel === '' && (
            <option value="" disabled>
              {rankingTexts.chooseLevel}
            </option>
          )}
          {LEVELS.map((level) => (
            <option key={level} value={level}>
              {texts.levels[level]}
            </option>
          ))}
        </select>
      </label>

      {renderBody()}
    </>
  );
}
