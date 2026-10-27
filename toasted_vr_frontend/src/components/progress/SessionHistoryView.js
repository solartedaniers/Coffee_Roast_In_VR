import React, { useState } from 'react';
import SessionHistoryTab from './SessionHistoryTab';
import SessionDetailView from './SessionDetailView';

// Historial del jugador (RF016): la lista y, al elegir una sesión, su detalle.
export default function SessionHistoryView({ texts }) {
  const [selectedSessionId, setSelectedSessionId] = useState(null);

  return selectedSessionId == null ? (
    <SessionHistoryTab texts={texts} onSelectSession={setSelectedSessionId} />
  ) : (
    <SessionDetailView texts={texts} sessionId={selectedSessionId} onBack={() => setSelectedSessionId(null)} />
  );
}
