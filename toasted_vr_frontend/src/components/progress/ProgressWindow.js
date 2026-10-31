import React from 'react';
import useEscapeKey from '../../hooks/useEscapeKey';
import useBodyScrollLock from '../../hooks/useBodyScrollLock';

// Ventana modal de la barra del jugador. Recibe el título y el contenido que
// debe mostrar (historial o ranking); el diseño es el mismo para las dos.
export default function ProgressWindow({ title, closeLabel, onClose, children }) {
  useEscapeKey(onClose);
  useBodyScrollLock();

  const handleBackdropClick = (event) => {
    if (event.target === event.currentTarget) onClose();
  };

  return (
    <div
      className="profile-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={handleBackdropClick}
    >
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
