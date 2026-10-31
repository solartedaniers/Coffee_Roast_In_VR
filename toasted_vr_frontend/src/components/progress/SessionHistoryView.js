import React, { useState } from 'react';
import SessionHistoryTab from './SessionHistoryTab';
import SessionDetailView from './SessionDetailView';
import useEscapeKey from '../../hooks/useEscapeKey';

// Historial del jugador (RF016): la lista y, al elegir una sesión, su detalle.
// Desde el detalle, Esc regresa a la lista en vez de cerrar la ventana.
export default function SessionHistoryView({ texts }) {
  const [selectedSessionId, setSelectedSessionId] = useState(null);
  const backToList = () => setSelectedSessionId(null);
  useEscapeKey(backToList, selectedSessionId != null);

  return selectedSessionId == null ? (
    <SessionHistoryTab texts={texts} onSelectSession={setSelectedSessionId} />
  ) : (
    <SessionDetailView texts={texts} sessionId={selectedSessionId} onBack={backToList} />
  );
}
