import React from 'react';

// ================================================================
// HelpPanel
// Responsabilidad única: la pantalla de Ayuda — texto estático que
// explica las fases del tueste. Sin lógica, solo presentación.
// ================================================================
export default function HelpPanel({ texts, isOpen, onClose }) {
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
        {texts.paragraphs.map((paragraph) => (
          <p className="sim-hint-text" key={paragraph}>{paragraph}</p>
        ))}
      </div>
    </div>
  );
}
