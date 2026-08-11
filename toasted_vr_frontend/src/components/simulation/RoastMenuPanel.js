import React from 'react';

// ================================================================
// RoastMenuPanel
// Responsabilidad única: el menú desplegable del botón "MENU" del
// HMI — configuración general, reinicio de gráfica y salida del
// tueste. El modo de operación ya no se cambia aquí: solo desde
// Configuración general, para que el tueste arranque siempre en
// manual y el operador tenga que decidir explícitamente pasar a
// automático.
// ================================================================
export default function RoastMenuPanel({ texts, isOpen, onClose, onOpenSettings, onResetChart, onAbort }) {
  if (!isOpen) return null;

  return (
    <>
      <button type="button" className="roast-menu-backdrop" aria-label={texts.close} onClick={onClose} />
      <div className="roast-menu-panel" role="menu">
        <button type="button" className="roast-menu-item" role="menuitem" onClick={onOpenSettings}>
          {texts.openGeneralSettings}
        </button>
        <button type="button" className="roast-menu-item" role="menuitem" onClick={onResetChart}>
          {texts.resetChart}
        </button>
        <button type="button" className="roast-menu-item roast-menu-item-danger" role="menuitem" onClick={onAbort}>
          {texts.abort}
        </button>
      </div>
    </>
  );
}
