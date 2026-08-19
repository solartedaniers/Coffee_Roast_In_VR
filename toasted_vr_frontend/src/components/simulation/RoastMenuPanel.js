import React from 'react';
import { OPERATION_MODES } from '../../domain/roasting/RoastConstants';

// ================================================================
// RoastMenuPanel
// Responsabilidad única: el menú del botón "MENU" del HMI, como una
// cuadrícula de recuadros (no una lista desplegable) — 5 opciones:
// Control de Tueste Automático (cambia a modo AUTO y abre la
// selección de curvas), Visualizar Proceso de Tueste, Control de
// Tueste Manual (resaltado en verde cuando es el modo activo),
// Configuración Interna y Guardar Datos. Ayuda y Salir del tueste
// viven en la misma esquina, como recuadros más chicos y aparte de
// las 5 opciones principales.
// ================================================================
export default function RoastMenuPanel({
  texts,
  isOpen,
  onClose,
  operationMode,
  onOpenAutoControl,
  onOpenManualControl,
  onOpenSettings,
  onOpenDataHistory,
  onOpenHelp,
  onAbort,
}) {
  if (!isOpen) return null;

  const isManualActive = operationMode === OPERATION_MODES.MANUAL;

  return (
    <>
      <button type="button" className="roast-menu-backdrop" aria-label={texts.close} onClick={onClose} />
      <div className="roast-menu-panel" role="menu">
        <div className="roast-menu-tiles-main">
          <button type="button" className="roast-menu-tile roast-menu-tile-large" role="menuitem" onClick={onOpenAutoControl}>
            <span className="roast-menu-tile-title">{texts.autoControl}</span>
            <span className="roast-menu-tile-subtitle">{texts.autoControlSubtitle}</span>
          </button>
          <button type="button" className="roast-menu-tile roast-menu-tile-large" role="menuitem" onClick={onClose}>
            <span className="roast-menu-tile-title">{texts.viewProcess}</span>
          </button>
          <button
            type="button"
            className={`roast-menu-tile roast-menu-tile-large${isManualActive ? ' roast-menu-tile-active' : ''}`}
            role="menuitem"
            onClick={onOpenManualControl}
          >
            <span className="roast-menu-tile-title">{texts.manualControl}</span>
          </button>
        </div>

        <div className="roast-menu-tiles-secondary">
          <button type="button" className="roast-menu-tile roast-menu-tile-small" role="menuitem" onClick={onOpenSettings}>
            {texts.openGeneralSettings}
          </button>
          <button type="button" className="roast-menu-tile roast-menu-tile-small" role="menuitem" onClick={onOpenDataHistory}>
            {texts.dataHistory}
          </button>
        </div>

        <div className="roast-menu-corner">
          <button type="button" className="roast-menu-tile roast-menu-tile-help" role="menuitem" onClick={onOpenHelp}>
            {texts.helpButton}
          </button>
          <button type="button" className="roast-menu-tile roast-menu-tile-danger" role="menuitem" onClick={onAbort}>
            {texts.abort}
          </button>
        </div>
      </div>
    </>
  );
}
