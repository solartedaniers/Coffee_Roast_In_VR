import React, { useId, useState } from 'react';
import ClockIcon from '../icons/ClockIcon';
import CrownIcon from '../icons/CrownIcon';
import ProgressWindow from './ProgressWindow';
import SessionHistoryView from './SessionHistoryView';
import RankingTab from './RankingTab';
import { markRankingSeen } from '../../services/progressService';
import { useRankingChanges } from '../../hooks/useRankingChanges';

const ADMIN_ROLE = 'ADMIN';
const WINDOWS = Object.freeze({ HISTORY: 'history', RANKING: 'ranking' });

// Botones de icono de la barra superior del jugador: el reloj abre la ventana
// del historial y la corona la del ranking, cada una con solo su contenido.
// La corona muestra un punto cuando el ranking cambió desde la última visita.
// Los administradores no ven la corona porque no participan en el ranking.
// El contenido se monta al abrir, así cada apertura trae datos recientes.
export default function PlayerProgressButtons({ texts, currentUser }) {
  const [openWindow, setOpenWindow] = useState(null);
  const showRanking = currentUser?.role !== ADMIN_ROLE;
  const { hasChanges, refresh } = useRankingChanges(showRanking);
  const noticeId = useId();
  const closeWindow = () => setOpenWindow(null);

  // Guarda como visto exactamente el ranking mostrado y vuelve a preguntar:
  // si cambió mientras tanto, el punto sigue encendido.
  const handleRankingShown = async (ranking) => {
    if (!ranking.signature) return;
    try {
      await markRankingSeen(ranking.signature);
    } catch {
      // Si no se pudo marcar, el punto se mantiene hasta la próxima visita.
    }
    refresh();
  };

  const renderButton = (windowName, label, Icon, { className = '', describedBy, badge } = {}) => (
    <button
      type="button"
      className={`secondary-button player-progress-btn ${className}`}
      aria-label={label}
      aria-describedby={describedBy}
      title={label}
      onClick={() => setOpenWindow(windowName)}
    >
      <Icon />
      {badge}
    </button>
  );

  return (
    <>
      <div className="player-progress-actions">
        {renderButton(WINDOWS.HISTORY, texts.historyTitle, ClockIcon)}
        {showRanking &&
          renderButton(WINDOWS.RANKING, texts.rankingTitle, CrownIcon, {
            className: 'player-progress-btn-crown',
            describedBy: hasChanges ? noticeId : undefined,
            badge: hasChanges && <span className="player-progress-dot" data-testid="ranking-changes-dot" />,
          })}
        {hasChanges && (
          <span id={noticeId} className="visually-hidden">
            {texts.ranking.changesNotice}
          </span>
        )}
      </div>

      {openWindow === WINDOWS.HISTORY && (
        <ProgressWindow title={texts.historyTitle} closeLabel={texts.close} onClose={closeWindow}>
          <SessionHistoryView texts={texts} />
        </ProgressWindow>
      )}

      {openWindow === WINDOWS.RANKING && (
        <ProgressWindow title={texts.rankingTitle} closeLabel={texts.close} onClose={closeWindow}>
          <RankingTab texts={texts} onLoaded={handleRankingShown} />
        </ProgressWindow>
      )}
    </>
  );
}
