import React, { useEffect, useState } from 'react';
import PasswordField from './PasswordField';
import { updateProfile } from '../services/profileService';

const PLAYER_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const MAX_PROFILE_IMAGE_BYTES = 900 * 1024;

const initialPasswordFields = {
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
};

function buildInitialForm(user) {
  return {
    name: user.name || '',
    username: user.username || '',
    profileImageUrl: user.profileImageUrl || '',
    knowledgeLevel: user.knowledgeLevel || '',
    ...initialPasswordFields,
  };
}

export default function ProfileSettings({
  texts,
  knowledgeTexts,
  currentUser,
  isOpen,
  onClose,
  onUserUpdate,
}) {
  const [formData, setFormData] = useState(() => buildInitialForm(currentUser));
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setFormData({ ...buildInitialForm(currentUser), ...initialPasswordFields });
      setStatus({ text: '', isError: false });
    }
  }, [currentUser, isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((current) => ({ ...current, [name]: value }));
  };

  const handleImageChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setStatus({ text: texts.messages.invalidImage, isError: true });
      return;
    }

    if (file.size > MAX_PROFILE_IMAGE_BYTES) {
      setStatus({ text: texts.messages.imageTooLarge, isError: true });
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      setFormData((current) => ({ ...current, profileImageUrl: reader.result }));
      setStatus({ text: '', isError: false });
    };
    reader.onerror = () => setStatus({ text: texts.messages.invalidImage, isError: true });
    reader.readAsDataURL(file);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (formData.newPassword && formData.newPassword !== formData.confirmPassword) {
      setStatus({ text: texts.messages.passwordMismatch, isError: true });
      return;
    }

    if (formData.newPassword && !formData.currentPassword) {
      setStatus({ text: texts.messages.currentPasswordRequired, isError: true });
      return;
    }

    setIsSaving(true);
    setStatus({ text: '', isError: false });

    try {
      const updatedUser = await updateProfile({
        name: currentUser.name,
        username: formData.username,
        profileImageUrl: formData.profileImageUrl || null,
        knowledgeLevel: currentUser.role === 'PLAYER' ? formData.knowledgeLevel || null : null,
        currentPassword: formData.currentPassword || null,
        newPassword: formData.newPassword || null,
      });
      setFormData({ ...buildInitialForm(updatedUser), ...initialPasswordFields });
      onUserUpdate(updatedUser);
      onClose();
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsSaving(false);
    }
  };

  const avatarLabel = formData.name?.charAt(0)?.toUpperCase() || formData.username?.charAt(0)?.toUpperCase() || '?';

  return (
    <div className="profile-modal-backdrop" role="presentation">
      <section className="profile-modal" role="dialog" aria-modal="true" aria-label={texts.ariaLabel}>
        <header className="profile-modal-header">
          <button type="button" className="secondary-button profile-close-button" onClick={onClose}>
            {texts.buttons.close}
          </button>
        </header>

        <form className="profile-form" onSubmit={handleSubmit}>
          <div className="profile-photo-row">
            <label className="profile-photo-picker">
              <span className="profile-avatar-preview">
                {formData.profileImageUrl ? (
                  <img src={formData.profileImageUrl} alt={texts.photo.alt} />
                ) : (
                  <span>{avatarLabel}</span>
                )}
              </span>
              <span className="text-link profile-photo-action">
                {formData.profileImageUrl ? texts.photo.edit : texts.photo.add}
              </span>
              <input type="file" accept="image/*" onChange={handleImageChange} />
            </label>
          </div>

          <label className="field-group">
            <span className="field-label">{texts.labels.username}</span>
            <input
              className="field-input"
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              required
            />
          </label>

          {currentUser.role === 'PLAYER' && (
            <label className="field-group">
              <span className="field-label">{texts.labels.knowledgeLevel}</span>
              <select
                className="field-input"
                name="knowledgeLevel"
                value={formData.knowledgeLevel}
                onChange={handleChange}
                required
              >
                {PLAYER_LEVELS.map((level) => (
                  <option key={level} value={level}>
                    {knowledgeTexts.options[level].label}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="profile-password-section">
            <span className="profile-section-title">{texts.passwordSection}</span>
            <PasswordField
              name="currentPassword"
              value={formData.currentPassword}
              onChange={handleChange}
              placeholder={texts.placeholders.currentPassword}
              label={texts.labels.currentPassword}
              required={false}
            />
            <div className="profile-grid">
              <PasswordField
                name="newPassword"
                value={formData.newPassword}
                onChange={handleChange}
                placeholder={texts.placeholders.newPassword}
                label={texts.labels.newPassword}
                required={false}
              />
              <PasswordField
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleChange}
                placeholder={texts.placeholders.confirmPassword}
                label={texts.labels.confirmPassword}
                required={false}
              />
            </div>
          </div>

          {status.text && (
            <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
              {status.text}
            </p>
          )}

          <button type="submit" className="primary-button profile-save-button" disabled={isSaving}>
            {isSaving ? texts.buttons.saving : texts.buttons.save}
          </button>
        </form>
      </section>
    </div>
  );
}
