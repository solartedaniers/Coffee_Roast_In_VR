import React, { useState } from 'react';
import SessionHistoryTab from '../progress/SessionHistoryTab';
import SessionDetailView from '../progress/SessionDetailView';

// ================================================================
// RoastDataHistoryPanel
// Responsabilidad única: la pantalla "Guardar Datos" del menú. Muestra el
// historial real del jugador (RF016) y el detalle de una sesión. El
// contenido se monta al abrir, así cada apertura trae los tuestes recién
// guardados.
// ================================================================
function HistoryContent({ texts }) {
  const [selectedSessionId, setSelectedSessionId] = useState(null);

  return selectedSessionId == null ? (
    <SessionHistoryTab texts={texts} onSelectSession={setSelectedSessionId} />
  ) : (
    <SessionDetailView texts={texts} sessionId={selectedSessionId} onBack={() => setSelectedSessionId(null)} />
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
