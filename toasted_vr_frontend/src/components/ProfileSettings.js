import React, { useEffect, useState } from 'react';
import FieldError from './FieldError';
import PasswordField from './PasswordField';
import {
  createUnityAccessCode,
  emailUnityAccessCode,
  fetchUnityAccessCodeStatus,
  regenerateUnityAccessCode,
  revealUnityAccessCode,
  updateProfile,
} from '../services/profileService';
import { useFieldErrors } from '../hooks/useFieldErrors';
import { getFieldErrors, hasFieldErrors } from '../utils/errorMessages';

const PLAYER_LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const MAX_PROFILE_IMAGE_BYTES = 900 * 1024;
const UNITY_ACTIONS = {
  create: 'create',
  reveal: 'reveal',
  email: 'email',
  regenerate: 'regenerate',
};

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
  const [isEditing, setIsEditing] = useState(false);
  const [isPasswordChangeVisible, setIsPasswordChangeVisible] = useState(false);
  const [unityCodeStatus, setUnityCodeStatus] = useState(null);
  const [isUnityStatusLoading, setIsUnityStatusLoading] = useState(false);
  const [unityStatusMessage, setUnityStatusMessage] = useState({ text: '', isError: false });
  const [unityAction, setUnityAction] = useState(null);
  const [unityCurrentPassword, setUnityCurrentPassword] = useState('');
  const [unityCode, setUnityCode] = useState('');
  const [isUnityActionLoading, setIsUnityActionLoading] = useState(false);
  const { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors } = useFieldErrors();

  const clearUnitySensitiveState = () => {
    setUnityAction(null);
    setUnityCurrentPassword('');
    setUnityCode('');
    setIsUnityActionLoading(false);
  };

  const loadUnityCodeStatus = async () => {
    setIsUnityStatusLoading(true);
    setUnityStatusMessage({ text: '', isError: false });

    try {
      const nextStatus = await fetchUnityAccessCodeStatus();
      setUnityCodeStatus(nextStatus);
    } catch {
      setUnityStatusMessage({ text: texts.unityAccess.messages.statusError, isError: true });
    } finally {
      setIsUnityStatusLoading(false);
    }
  };

  useEffect(() => {
    if (isOpen) {
      setFormData({ ...buildInitialForm(currentUser), ...initialPasswordFields });
      setStatus({ text: '', isError: false });
      setIsEditing(false);
      setIsPasswordChangeVisible(false);
      setUnityCodeStatus(null);
      clearUnitySensitiveState();
      loadUnityCodeStatus();
    } else {
      clearUnitySensitiveState();
    }
    // The modal lifecycle intentionally resets all sensitive Unity state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser, isOpen]);

  if (!isOpen) {
    return null;
  }

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((current) => ({ ...current, [name]: value }));
    clearFieldError(name);
  };

  const handleStartEditing = () => {
    setStatus({ text: '', isError: false });
    setIsEditing(true);
    setIsPasswordChangeVisible(false);
  };

  const handleCancelEditing = () => {
    setFormData({ ...buildInitialForm(currentUser), ...initialPasswordFields });
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();
    setIsEditing(false);
    setIsPasswordChangeVisible(false);
  };

  const handleStartPasswordChange = () => {
    setStatus({ text: '', isError: false });
    setIsPasswordChangeVisible(true);
  };

  const handleCancelPasswordChange = () => {
    setFormData((current) => ({ ...current, ...initialPasswordFields }));
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();
    setIsPasswordChangeVisible(false);
  };

  const handleClose = () => {
    setFormData((current) => ({ ...current, ...initialPasswordFields }));
    clearUnitySensitiveState();
    onClose();
  };

  const handleOpenUnityAction = (action) => {
    setUnityStatusMessage({ text: '', isError: false });
    setUnityCurrentPassword('');
    setUnityCode('');
    setUnityAction(action);
  };

  const handleCloseUnityAction = () => {
    clearUnitySensitiveState();
  };

  const handleUnityAction = async (event) => {
    event.preventDefault();

    if (!unityCurrentPassword.trim()) {
      setUnityStatusMessage({ text: texts.unityAccess.messages.passwordRequired, isError: true });
      return;
    }

    setIsUnityActionLoading(true);
    setUnityStatusMessage({ text: '', isError: false });

    try {
      let response;
      if (unityAction === UNITY_ACTIONS.create) {
        response = await createUnityAccessCode(unityCurrentPassword);
      } else if (unityAction === UNITY_ACTIONS.reveal) {
        response = await revealUnityAccessCode(unityCurrentPassword);
      } else if (unityAction === UNITY_ACTIONS.email) {
        await emailUnityAccessCode(unityCurrentPassword);
      } else if (unityAction === UNITY_ACTIONS.regenerate) {
        response = await regenerateUnityAccessCode(unityCurrentPassword);
      }

      setUnityCurrentPassword('');

      if (unityAction === UNITY_ACTIONS.email) {
        setUnityAction(null);
        setUnityCode('');
        await loadUnityCodeStatus();
        setUnityStatusMessage({ text: texts.unityAccess.messages.emailSent, isError: false });
      } else {
        setUnityCode(response.code);
        await loadUnityCodeStatus();
      }
    } catch {
      setUnityCurrentPassword('');
      setUnityCode('');
      setUnityStatusMessage({ text: texts.unityAccess.messages.actionError, isError: true });
    } finally {
      setIsUnityActionLoading(false);
    }
  };

  const handleCopyUnityCode = async () => {
    try {
      await navigator.clipboard.writeText(unityCode);
      setUnityStatusMessage({ text: texts.unityAccess.messages.copied, isError: false });
    } catch {
      setUnityStatusMessage({ text: texts.unityAccess.messages.copyError, isError: true });
    }
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

    const validationErrors = {};
    if (formData.newPassword && formData.newPassword !== formData.confirmPassword) {
      validationErrors.confirmPassword = texts.messages.passwordMismatch;
    }

    if (formData.newPassword && !formData.currentPassword) {
      validationErrors.currentPassword = texts.messages.currentPasswordRequired;
    }

    if (hasFieldErrors(validationErrors)) {
      setFieldErrors(validationErrors);
      setStatus({ text: '', isError: false });
      return;
    }

    setIsSaving(true);
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();

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
      setIsEditing(false);
      setIsPasswordChangeVisible(false);
      clearUnitySensitiveState();
      onClose();
    } catch (error) {
      const apiFieldErrors = getFieldErrors(error);
      if (hasFieldErrors(apiFieldErrors)) {
        setFieldErrors(apiFieldErrors);
      } else {
        setStatus({ text: error.message, isError: true });
      }
    } finally {
      setIsSaving(false);
    }
  };

  const avatarLabel = formData.name?.charAt(0)?.toUpperCase() || formData.username?.charAt(0)?.toUpperCase() || '?';

  return (
    <div className="profile-modal-backdrop" role="presentation">
      <section className="profile-modal" role="dialog" aria-modal="true" aria-label={texts.ariaLabel}>
        <header className="profile-modal-header">
          <button type="button" className="secondary-button profile-close-button" onClick={handleClose}>
            {texts.buttons.close}
          </button>
        </header>

        <form className="profile-form" onSubmit={handleSubmit}>
          <div className="profile-photo-row">
            <div className="profile-photo-picker">
              <span className="profile-avatar-preview">
                {formData.profileImageUrl ? (
                  <img src={formData.profileImageUrl} alt={texts.photo.alt} />
                ) : (
                  <span>{avatarLabel}</span>
                )}
              </span>
              {isEditing && (
                <label className="text-link profile-photo-action">
                  {formData.profileImageUrl ? texts.photo.edit : texts.photo.add}
                  <input type="file" accept="image/*" onChange={handleImageChange} />
                </label>
              )}
            </div>
          </div>

          <div className="profile-details-grid">
            <label className="field-group">
              <span className="field-label">{texts.labels.name}</span>
              <input className="field-input" type="text" value={currentUser.name} readOnly />
            </label>

            <label className="field-group">
              <span className="field-label">{texts.labels.email}</span>
              <input className="field-input" type="email" value={currentUser.email} readOnly />
            </label>

            <label className="field-group">
              <span className="field-label">{texts.labels.username}</span>
              <input
                className="field-input"
                type="text"
                name="username"
                value={formData.username}
                onChange={handleChange}
                readOnly={!isEditing}
                aria-invalid={Boolean(fieldErrors.username)}
                required
              />
              <FieldError message={fieldErrors.username} />
            </label>

            {currentUser.role === 'PLAYER' && (
              <label className="field-group">
                <span className="field-label">{texts.labels.knowledgeLevel}</span>
                <select
                  className="field-input"
                  name="knowledgeLevel"
                  value={formData.knowledgeLevel}
                  onChange={handleChange}
                  disabled={!isEditing}
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
          </div>

          {isEditing && !isPasswordChangeVisible && (
            <button type="button" className="text-link profile-password-toggle" onClick={handleStartPasswordChange}>
              {texts.buttons.changePassword}
            </button>
          )}

          {isEditing && isPasswordChangeVisible && (
            <div className="profile-password-section">
              <span className="profile-section-title">{texts.passwordSection}</span>
              <PasswordField
                name="currentPassword"
                value={formData.currentPassword}
                onChange={handleChange}
                placeholder={texts.placeholders.currentPassword}
                label={texts.labels.currentPassword}
                required={isPasswordChangeVisible}
                error={fieldErrors.currentPassword}
              />
              <div className="profile-grid">
                <PasswordField
                  name="newPassword"
                  value={formData.newPassword}
                  onChange={handleChange}
                  placeholder={texts.placeholders.newPassword}
                  label={texts.labels.newPassword}
                  required={isPasswordChangeVisible}
                  error={fieldErrors.newPassword}
                />
                <PasswordField
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  placeholder={texts.placeholders.confirmPassword}
                  label={texts.labels.confirmPassword}
                  required={isPasswordChangeVisible}
                  error={fieldErrors.confirmPassword}
                />
              </div>
              <button type="button" className="text-link profile-password-toggle" onClick={handleCancelPasswordChange}>
                {texts.buttons.cancelPasswordChange}
              </button>
            </div>
          )}

          {status.text && (
            <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
              {status.text}
            </p>
          )}

          {isEditing ? (
            <div className="profile-actions">
              <button type="button" className="secondary-button" onClick={handleCancelEditing} disabled={isSaving}>
                {texts.buttons.cancel}
              </button>
              <button type="submit" className="primary-button profile-save-button" disabled={isSaving}>
                {isSaving ? texts.buttons.saving : texts.buttons.save}
              </button>
            </div>
          ) : (
            <button type="button" className="primary-button profile-save-button" onClick={handleStartEditing}>
              {texts.buttons.edit}
            </button>
          )}
        </form>

        <section className="unity-access-section" aria-labelledby="unity-access-title">
          <div className="unity-access-heading">
            <div>
              <h3 id="unity-access-title">{texts.unityAccess.title}</h3>
              <p>{texts.unityAccess.subtitle}</p>
            </div>
            {unityCodeStatus?.exists && (
              <span className="unity-access-badge">{texts.unityAccess.configured}</span>
            )}
          </div>

          {isUnityStatusLoading ? (
            <p className="unity-access-description" aria-live="polite">{texts.unityAccess.loading}</p>
          ) : unityCodeStatus?.exists ? (
            <>
              <p className="unity-access-description">{texts.unityAccess.configuredDescription}</p>
              <div className="unity-access-actions">
                <button type="button" className="secondary-button" onClick={() => handleOpenUnityAction(UNITY_ACTIONS.reveal)}>
                  {texts.unityAccess.buttons.reveal}
                </button>
                <button type="button" className="secondary-button" onClick={() => handleOpenUnityAction(UNITY_ACTIONS.email)}>
                  {texts.unityAccess.buttons.email}
                </button>
                <button type="button" className="text-link unity-regenerate-button" onClick={() => handleOpenUnityAction(UNITY_ACTIONS.regenerate)}>
                  {texts.unityAccess.buttons.regenerate}
                </button>
              </div>
            </>
          ) : !unityStatusMessage.isError ? (
            <>
              <p className="unity-access-description">{texts.unityAccess.notConfiguredDescription}</p>
              <button type="button" className="secondary-button unity-create-button" onClick={() => handleOpenUnityAction(UNITY_ACTIONS.create)}>
                {texts.unityAccess.buttons.create}
              </button>
            </>
          ) : (
            <button type="button" className="text-link unity-retry-button" onClick={loadUnityCodeStatus}>
              {texts.unityAccess.buttons.retry}
            </button>
          )}

          {unityStatusMessage.text && !unityAction && (
            <p className={`status-message ${unityStatusMessage.isError ? 'error' : 'success'}`} aria-live="polite">
              {unityStatusMessage.text}
            </p>
          )}
        </section>

        {unityAction && (
          <div className="unity-confirmation-backdrop" role="presentation">
            <section className="unity-confirmation-modal" role="dialog" aria-modal="true" aria-labelledby="unity-confirmation-title">
              <h3 id="unity-confirmation-title">{texts.unityAccess.confirmation[unityAction].title}</h3>
              <p>{unityCode ? texts.unityAccess.messages.codeReady : texts.unityAccess.confirmation[unityAction].description}</p>

              {unityCode ? (
                <div className="unity-code-result" aria-live="polite">
                  <strong>{unityCode}</strong>
                  <button type="button" className="primary-button" onClick={handleCopyUnityCode}>
                    {texts.unityAccess.buttons.copy}
                  </button>
                </div>
              ) : (
                <form className="unity-confirmation-form" onSubmit={handleUnityAction}>
                  <PasswordField
                    name="unityCurrentPassword"
                    value={unityCurrentPassword}
                    onChange={(event) => setUnityCurrentPassword(event.target.value)}
                    placeholder={texts.unityAccess.passwordPlaceholder}
                    label={texts.unityAccess.passwordLabel}
                    required
                  />
                  <div className="profile-actions">
                    <button type="button" className="secondary-button" onClick={handleCloseUnityAction} disabled={isUnityActionLoading}>
                      {texts.unityAccess.buttons.cancel}
                    </button>
                    <button type="submit" className="primary-button" disabled={isUnityActionLoading}>
                      {isUnityActionLoading ? texts.unityAccess.buttons.processing : texts.unityAccess.confirmation[unityAction].confirm}
                    </button>
                  </div>
                </form>
              )}

              {unityStatusMessage.text && (
                <p className={`status-message ${unityStatusMessage.isError ? 'error' : 'success'}`} aria-live="polite">
                  {unityStatusMessage.text}
                </p>
              )}

              {unityCode && (
                <button type="button" className="secondary-button unity-result-close" onClick={handleCloseUnityAction}>
                  {texts.unityAccess.buttons.close}
                </button>
              )}
            </section>
          </div>
        )}
      </section>
    </div>
  );
}
