import React, { useState } from 'react';
import SessionHistoryTab from '../progress/SessionHistoryTab';
import SessionDetailView from '../progress/SessionDetailView';
import RankingTab from '../progress/RankingTab';

const TABS = Object.freeze({ HISTORY: 'history', RANKING: 'ranking' });

// ================================================================
// RoastDataHistoryPanel
// Responsabilidad única: la pantalla "Historial y ranking" del menú, con
// dos pestañas: el historial del jugador con el detalle de cada sesión
// (RF016) y el ranking por nivel (RF017). El contenido se monta al abrir,
// así cada apertura trae los tuestes recién guardados.
// ================================================================
function HistoryContent({ texts }) {
  const [activeTab, setActiveTab] = useState(TABS.HISTORY);
  const [selectedSessionId, setSelectedSessionId] = useState(null);

  const renderTab = (tab) => (
    <button
      type="button"
      role="tab"
      aria-selected={activeTab === tab}
      className={`roast-menu-mode-btn ${activeTab === tab ? 'roast-menu-mode-btn-active' : ''}`}
      onClick={() => setActiveTab(tab)}
    >
      {texts.tabs[tab]}
    </button>
  );

  const renderHistory = () =>
    selectedSessionId == null ? (
      <SessionHistoryTab texts={texts} onSelectSession={setSelectedSessionId} />
    ) : (
      <SessionDetailView texts={texts} sessionId={selectedSessionId} onBack={() => setSelectedSessionId(null)} />
    );

  return (
    <>
      <div className="roast-menu-mode-toggle" role="tablist">
        {renderTab(TABS.HISTORY)}
        {renderTab(TABS.RANKING)}
      </div>
      {activeTab === TABS.HISTORY ? renderHistory() : <RankingTab texts={texts} />}
    </>
  );
}

export default function RoastDataHistoryPanel({ texts, isOpen, onClose }) {
  if (!isOpen) return null;

  return (
    <div className="profile-modal-backdrop" role="dialog" aria-modal="true" aria-label={texts.title}>
      <div className="profile-modal settings-modal">
        <div className="profile-modal-header">
          <button type="button" className="secondary-button profile-close-button" onClick={onClose}>
            {texts.close}
          </button>
        </div>
        <h2>{texts.title}</h2>
        <HistoryContent texts={texts} />
      </div>
    </div>
  );
}
