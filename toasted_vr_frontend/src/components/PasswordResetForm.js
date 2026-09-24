import React, { useMemo, useState } from 'react';
import FieldError from './FieldError';
import PasswordField from './PasswordField';
import VerificationCodeInput from './VerificationCodeInput';
import { confirmPasswordReset, requestPasswordReset } from '../services/authService';
import { formatCountdown, useCountdown } from '../hooks/useCountdown';
import { useFieldErrors } from '../hooks/useFieldErrors';
import { getFieldErrors, hasFieldErrors, resolveErrorMessage } from '../utils/errorMessages';

const emptyCode = ['', '', '', '', '', ''];

// Segundo paso de la recuperación: código + nueva contraseña. El backend
// aplica los límites de reenvío en silencio (para no delatar la cuenta), así
// que aquí se reflejan con los valores de codePolicy para avisar al usuario.
function PasswordResetForm({ email, codePolicy, texts, errorTexts, onResetSuccess }) {
  const [codeDigits, setCodeDigits] = useState(emptyCode);
  const [passwords, setPasswords] = useState({ newPassword: '', confirmPassword: '' });
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [resendsUsed, setResendsUsed] = useState(0);
  const [secondsLeft, restartCountdown] = useCountdown(codePolicy.expiresInSeconds);
  const [resendLockSecondsLeft, startResendLock] = useCountdown(0);
  const { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors } = useFieldErrors();

  const code = useMemo(() => codeDigits.join(''), [codeDigits]);

  const handleDigitChange = (index, value) => {
    setCodeDigits((currentValue) =>
      currentValue.map((digit, digitIndex) => (digitIndex === index ? value : digit))
    );
    clearFieldError('code');
  };

  const handlePasswordChange = (event) => {
    const { name, value } = event.target;
    setPasswords((currentValue) => ({ ...currentValue, [name]: value }));
    clearFieldError(name);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const validationErrors = {};
    if (code.length !== 6) {
      validationErrors.code = texts.messages.invalidCodeLength;
    }
    if (passwords.newPassword !== passwords.confirmPassword) {
      validationErrors.confirmPassword = texts.messages.passwordMismatch;
    }
    if (hasFieldErrors(validationErrors)) {
      setFieldErrors(validationErrors);
      setStatus({ text: '', isError: false });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();

    try {
      await confirmPasswordReset({ email, code, ...passwords });
      onResetSuccess();
    } catch (error) {
      const apiFieldErrors = getFieldErrors(error);
      if (hasFieldErrors(apiFieldErrors)) {
        setFieldErrors(apiFieldErrors);
      } else if (error.code === 'INVALID_CODE') {
        setFieldErrors({ code: texts.messages.invalidCode });
      } else {
        setStatus({ text: resolveErrorMessage(error, errorTexts), isError: true });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    if (resendsUsed >= codePolicy.maxResends) {
      startResendLock(codePolicy.resendLockMinutes * 60);
      setResendsUsed(0);
      setStatus({ text: errorTexts.TOO_MANY_RESENDS, isError: true });
      return;
    }

    setIsResending(true);
    setStatus({ text: '', isError: false });

    try {
      const response = await requestPasswordReset({ email });
      setResendsUsed((currentValue) => currentValue + 1);
      setCodeDigits(emptyCode);
      clearFieldError('code');
      restartCountdown(response.codePolicy.expiresInSeconds);
      setStatus({ text: texts.messages.codeResent, isError: false });
    } catch (error) {
      setStatus({ text: resolveErrorMessage(error, errorTexts), isError: true });
    } finally {
      setIsResending(false);
    }
  };

  return (
    <section className="verification-panel">
      <div className="verification-badge">{texts.badge}</div>
      <p className="verification-copy">
        {texts.instructions}{' '}
        <span className="verification-email">{email}</span>
      </p>
      <p className={`countdown ${secondsLeft === 0 ? 'expired' : ''}`}>
        {texts.timerLabel} <span>{formatCountdown(secondsLeft)}</span>
      </p>
      {resendLockSecondsLeft > 0 && (
        <p className="countdown expired">
          {texts.resendLockLabel} <span>{formatCountdown(resendLockSecondsLeft)}</span>
        </p>
      )}

      <form className="form-grid" onSubmit={handleSubmit}>
        <div className="field-group">
          <VerificationCodeInput codeDigits={codeDigits} onChange={handleDigitChange} />
          <FieldError message={fieldErrors.code} />
        </div>

        <PasswordField
          name="newPassword"
          value={passwords.newPassword}
          onChange={handlePasswordChange}
          placeholder={texts.placeholders.newPassword}
          label={texts.labels.newPassword}
          error={fieldErrors.newPassword}
        />

        <PasswordField
          name="confirmPassword"
          value={passwords.confirmPassword}
          onChange={handlePasswordChange}
          placeholder={texts.placeholders.confirmPassword}
          label={texts.labels.confirmPassword}
          error={fieldErrors.confirmPassword}
        />

        {status.text && (
          <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
            {status.text}
          </p>
        )}

        <div className="action-row">
          <button className="primary-button" type="submit" disabled={isLoading}>
            {isLoading ? texts.buttons.resetting : texts.buttons.reset}
          </button>
          {secondsLeft === 0 && (
            <button
              className="secondary-button"
              type="button"
              onClick={handleResend}
              disabled={isResending || resendLockSecondsLeft > 0}
            >
              {isResending ? texts.buttons.resending : texts.buttons.resend}
            </button>
          )}
        </div>
      </form>
    </section>
  );
}

export default PasswordResetForm;
