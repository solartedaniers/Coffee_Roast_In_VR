import React from 'react';
import { TARGET_TEMP_MIN_C, TARGET_TEMP_MAX_C } from '../../domain/roasting/RoastConstants';

// ================================================================
// CurveProgrammingPanel
// Responsabilidad única: la pantalla "Programación de Curvas" del
// menú — la tabla 3×3 (Curva 1/2/3 × Inicio/Crepitación/Final) donde
// el operador define temperaturas de referencia. Solo presentación:
// no aplica las curvas a ningún tueste todavía (ver RoastCurveProfile.js).
// ================================================================
export default function CurveProgrammingPanel({ texts, isOpen, onClose, curves, onChangeCurve }) {
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
        <p className="sim-hint-text">{texts.subtitle}</p>

        <table className="curve-programming-table">
          <thead>
            <tr>
              <th>{texts.curveColumn}</th>
              <th>{texts.startColumn}</th>
              <th>{texts.crackleColumn}</th>
              <th>{texts.finalColumn}</th>
            </tr>
          </thead>
          <tbody>
            {curves.map((curve) => (
              <tr key={curve.curveNumber}>
                <td>{texts.curveLabel} {curve.curveNumber}</td>
                {['startTemp', 'crackleTemp', 'finalTemp'].map((field) => (
                  <td key={field}>
                    <input
                      type="number"
                      className="field-input"
                      min={TARGET_TEMP_MIN_C}
                      max={TARGET_TEMP_MAX_C}
                      step={1}
                      value={curve[field]}
                      onChange={(event) => onChangeCurve(curve.curveNumber, field, Number(event.target.value))}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
