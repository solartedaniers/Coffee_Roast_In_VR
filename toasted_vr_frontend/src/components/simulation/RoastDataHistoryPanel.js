import React from 'react';

// ================================================================
// RoastDataHistoryPanel
// Responsabilidad única: la pantalla "Guardar Datos" del menú. Solo
// presentación por ahora — no hay endpoint en el backend para listar
// sesiones guardadas todavía, así que muestra un aviso en vez de
// datos reales (ver Parte 3 del plan).
// ================================================================
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
        <p className="sim-hint-text">{texts.unavailable}</p>
      </div>
    </div>
  );
}
