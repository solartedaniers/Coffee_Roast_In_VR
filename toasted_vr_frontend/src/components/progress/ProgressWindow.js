import React from 'react';

// Ventana modal de la barra del jugador. Recibe el título y el contenido que
// debe mostrar (historial o ranking); el diseño es el mismo para las dos.
export default function ProgressWindow({ title, closeLabel, onClose, children }) {
  return (
    <div className="profile-modal-backdrop" role="dialog" aria-modal="true" aria-label={title}>
      <div className="profile-modal settings-modal">
        <div className="profile-modal-header">
          <button type="button" className="secondary-button profile-close-button" onClick={onClose}>
            {closeLabel}
          </button>
        </div>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}
