import React, { useState } from 'react';
import ClockIcon from '../icons/ClockIcon';
import CrownIcon from '../icons/CrownIcon';
import ProgressWindow from './ProgressWindow';
import SessionHistoryView from './SessionHistoryView';
import RankingTab from './RankingTab';

const ADMIN_ROLE = 'ADMIN';
const WINDOWS = Object.freeze({ HISTORY: 'history', RANKING: 'ranking' });

// Botones de icono de la barra superior del jugador: el reloj abre la ventana
// del historial y la corona la del ranking, cada una con solo su contenido.
// Los administradores no ven la corona porque no participan en el ranking.
// El contenido se monta al abrir, así cada apertura trae datos recientes.
export default function PlayerProgressButtons({ texts, currentUser }) {
  const [openWindow, setOpenWindow] = useState(null);
  const showRanking = currentUser?.role !== ADMIN_ROLE;
  const closeWindow = () => setOpenWindow(null);

  const renderButton = (windowName, label, Icon, extraClass = '') => (
    <button
      type="button"
      className={`secondary-button player-progress-btn ${extraClass}`}
      aria-label={label}
      title={label}
      onClick={() => setOpenWindow(windowName)}
    >
      <Icon />
    </button>
  );

  return (
    <>
      <div className="player-progress-actions">
        {renderButton(WINDOWS.HISTORY, texts.historyTitle, ClockIcon)}
        {showRanking && renderButton(WINDOWS.RANKING, texts.rankingTitle, CrownIcon, 'player-progress-btn-crown')}
      </div>

      {openWindow === WINDOWS.HISTORY && (
        <ProgressWindow title={texts.historyTitle} closeLabel={texts.close} onClose={closeWindow}>
          <SessionHistoryView texts={texts} />
        </ProgressWindow>
      )}

      {openWindow === WINDOWS.RANKING && (
        <ProgressWindow title={texts.rankingTitle} closeLabel={texts.close} onClose={closeWindow}>
          <RankingTab texts={texts} />
        </ProgressWindow>
      )}
    </>
  );
}
