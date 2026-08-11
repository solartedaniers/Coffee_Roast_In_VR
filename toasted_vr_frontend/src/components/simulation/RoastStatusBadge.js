import React from 'react';
import { PHASES } from '../../domain/roasting/RoastConstants';

const TONE_BY_PHASE = Object.freeze({
  [PHASES.PREHEAT]: 'heating',
  [PHASES.READY]: 'ready',
  [PHASES.CHARGE_DIP]: 'heating',
  [PHASES.ROASTING]: 'roasting',
  [PHASES.FINISHED]: 'finished',
});

// ================================================================
// RoastStatusBadge
// Responsabilidad única: mostrar la casilla grande y coloreada con
// el estado de la máquina ("CALENTANDO" / "TOSTANDO"...) del HMI
// de referencia.
// ================================================================
export default function RoastStatusBadge({ phase, label }) {
  const tone = TONE_BY_PHASE[phase] || 'idle';
  return (
    <div className={`status-indicator status-indicator-${tone}`}>
      <span>{label}</span>
    </div>
  );
}
