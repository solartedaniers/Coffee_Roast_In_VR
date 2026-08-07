import React, { useState } from 'react';

// ================================================================
// EditableNumberField
// Responsabilidad única: renderizar una casilla estilo HMI que es
// de solo lectura (una lectura de sensor en vivo) o editable con un
// toque (un valor que ajusta el operador) — clic en el valor,
// escribir uno nuevo, Enter/blur confirma.
// ================================================================
export default function EditableNumberField({
  label,
  value,
  unit,
  min,
  max,
  step = 1,
  editable = false,
  tone = 'neutral',
  onCommit,
  editAriaLabel,
}) {
  const [isEditing, setIsEditing] = useState(false);
  const [draftValue, setDraftValue] = useState(value);

  const beginEdit = () => {
    if (!editable) return;
    setDraftValue(value);
    setIsEditing(true);
  };

  const commit = () => {
    const parsed = Number(draftValue);
    if (Number.isFinite(parsed)) {
      const clamped = Math.min(max, Math.max(min, parsed));
      onCommit(clamped);
    }
    setIsEditing(false);
  };

  const cancel = () => {
    setIsEditing(false);
  };

  const handleKeyDown = (event) => {
    if (event.key === 'Enter') commit();
    if (event.key === 'Escape') cancel();
  };

  return (
    <div className={`hmi-tile hmi-tile-${tone}${editable ? ' hmi-tile-editable' : ''}`}>
      <span className="hmi-tile-label">{label}</span>
      {isEditing ? (
        <input
          type="number"
          className="hmi-tile-input"
          value={draftValue}
          min={min}
          max={max}
          step={step}
          autoFocus
          onChange={(event) => setDraftValue(event.target.value)}
          onBlur={commit}
          onKeyDown={handleKeyDown}
          aria-label={editAriaLabel}
        />
      ) : (
        <button
          type="button"
          className="hmi-tile-value"
          onClick={beginEdit}
          disabled={!editable}
          aria-label={editable ? editAriaLabel : undefined}
        >
          {value}
          {unit && <span className="hmi-tile-unit">{unit}</span>}
        </button>
      )}
    </div>
  );
}
