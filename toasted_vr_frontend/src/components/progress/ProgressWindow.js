import React from 'react';
import { createPortal } from 'react-dom';
import useEscapeKey from '../../hooks/useEscapeKey';
import useBodyScrollLock from '../../hooks/useBodyScrollLock';

// Ventana modal de la barra del jugador. Recibe el título y el contenido que
// debe mostrar (historial o ranking); el diseño es el mismo para las dos.
// Usa las clases de las demás ventanas más un modificador propio
// (progress-window), así su tamaño no afecta a Perfil, Ayuda ni Configuración.
// Se monta en body porque la cabecera del simulador encierra a position: fixed
// y el fondo no alcanzaba a cubrir toda la pantalla.
export default function ProgressWindow({ title, closeLabel, onClose, children }) {
  useEscapeKey(onClose);
  useBodyScrollLock();

  const handleBackdropClick = (event) => {
    if (event.target === event.currentTarget) onClose();
  };

  return createPortal(
    <div
      className="profile-modal-backdrop progress-window-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={handleBackdropClick}
    >
      <div className="profile-modal settings-modal progress-window">
        <div className="profile-modal-header progress-window-header">
          <h2>{title}</h2>
          <button type="button" className="secondary-button profile-close-button" onClick={onClose}>
            {closeLabel}
          </button>
        </div>
        <div className="progress-window-body">{children}</div>
      </div>
    </div>,
    document.body
  );
}
