import React, { useState } from 'react';
import './KnowledgeLevelSelection.css';
import { updateKnowledgeLevel } from '../services/knowledgeLevelService';

const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

const LEVEL_ICONS = {
  BEGINNER: '🌱',
  INTERMEDIATE: '☕',
  ADVANCED: '🔥',
};

export default function KnowledgeLevelSelection({ texts, currentUser, onSuccess, onLogout }) {
  const [selectedLevel, setSelectedLevel] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async () => {
    if (!selectedLevel || isLoading) return;

    setIsLoading(true);
    setError('');

    try {
      const updatedUser = await updateKnowledgeLevel(selectedLevel);
      onSuccess(updatedUser);
    } catch (err) {
      setError(texts.error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="kl-shell">
      <div className="ambient-light ambient-light-left" />
      <div className="ambient-light ambient-light-right" />

      <div className="kl-card">
        <header className="kl-header">
          <p className="kl-eyebrow">{currentUser.username}</p>
          <h1 className="kl-title">{texts.title}</h1>
          <p className="kl-subtitle">{texts.subtitle}</p>
        </header>

        <div className="kl-options" role="radiogroup" aria-label={texts.title}>
          {LEVELS.map((level) => {
            const option = texts.options[level];
            const isSelected = selectedLevel === level;
            return (
              <button
                key={level}
                type="button"
                role="radio"
                aria-checked={isSelected}
                className={`kl-option${isSelected ? ' kl-option-selected' : ''}`}
                onClick={() => setSelectedLevel(level)}
              >
                <div className="kl-option-icon" aria-hidden="true">
                  {LEVEL_ICONS[level]}
                </div>
                <span className="kl-option-label">{option.label}</span>
                <p className="kl-option-description">{option.description}</p>
                <p className="kl-option-hint">{option.hint}</p>
              </button>
            );
          })}
        </div>

        <footer className="kl-footer">
          <button
            type="button"
            className="primary-button kl-submit-btn"
            onClick={handleSubmit}
            disabled={!selectedLevel || isLoading}
          >
            {isLoading ? texts.loading : texts.button}
          </button>

          {error && (
            <p className="kl-error" role="alert" aria-live="assertive">
              {error}
            </p>
          )}

          <div className="kl-logout-row">
            <button type="button" className="text-link" onClick={onLogout}>
              {texts.logoutButton}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
