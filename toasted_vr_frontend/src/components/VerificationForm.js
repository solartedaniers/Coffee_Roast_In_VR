import React, { useEffect, useMemo, useState } from 'react';
import FieldError from './FieldError';
import VerificationCodeInput from './VerificationCodeInput';
import { resendVerificationCode, verifyEmailCode } from '../services/authService';
import { formatCountdown, useCountdown } from '../hooks/useCountdown';
import { resolveErrorMessage } from '../utils/errorMessages';

const emptyCode = ['', '', '', '', '', ''];

// Sin codePolicy (el usuario llega desde "Verificar mi cuenta" en el login) la
// cuenta regresiva arranca en 0 para que pueda pedir un código de inmediato;
// los límites llegan con la respuesta del reenvío.
function VerificationForm({ email, codePolicy = null, texts, errorTexts, onVerificationSuccess }) {
  const initialSeconds = codePolicy?.expiresInSeconds ?? 0;
  const [codeDigits, setCodeDigits] = useState(emptyCode);
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [codeError, setCodeError] = useState('');
  const [remainingAttempts, setRemainingAttempts] = useState(codePolicy?.maxAttempts ?? null);
  const [secondsLeft, restartCountdown] = useCountdown(initialSeconds);
  const [resendLockSecondsLeft, startResendLock] = useCountdown(0);

  const verificationCode = useMemo(() => codeDigits.join(''), [codeDigits]);

  useEffect(() => {
    restartCountdown(initialSeconds);
  }, [initialSeconds, email, restartCountdown]);

  const handleDigitChange = (index, value) => {
    setCodeDigits((currentValue) =>
      currentValue.map((digit, digitIndex) => (digitIndex === index ? value : digit))
    );
    setCodeError('');
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (verificationCode.length !== 6) {
      setCodeError(texts.messages.invalidCodeLength);
      setStatus({ text: '', isError: false });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });

    try {
      const response = await verifyEmailCode({ email, code: verificationCode });
      setStatus({ text: response.message, isError: false });
      onVerificationSuccess(response);
    } catch (error) {
      // Un código incorrecto pertenece al campo del código; el resto de errores va al cuadro general.
      if (error.code === 'INVALID_CODE') {
        if (error.details?.remainingAttempts != null) {
          setRemainingAttempts(error.details.remainingAttempts);
        }
        setCodeError(resolveErrorMessage(error, errorTexts));
        return;
      }

      if (error.code === 'CODE_ATTEMPTS_EXHAUSTED') {
        setRemainingAttempts(0);
      }

      // Un código agotado o vencido ya no sirve: se habilita el reenvío.
      if (error.code === 'CODE_ATTEMPTS_EXHAUSTED' || error.code === 'CODE_EXPIRED') {
        restartCountdown(0);
      }

      setStatus({ text: resolveErrorMessage(error, errorTexts), isError: true });
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    setIsResending(true);
    setStatus({ text: '', isError: false });

    try {
      const response = await resendVerificationCode({ email });
      setCodeDigits(emptyCode);
      setCodeError('');
      setRemainingAttempts(response.codePolicy.maxAttempts);
      restartCountdown(response.codePolicy.expiresInSeconds);
      setStatus({ text: texts.verification.codeResent, isError: false });
    } catch (error) {
      if (error.code === 'TOO_MANY_RESENDS') {
        startResendLock(error.details?.secondsRemaining ?? 0);
      }

      if (error.code === 'CODE_STILL_ACTIVE') {
        restartCountdown(error.details?.secondsRemaining ?? 0);
      }

      setStatus({ text: resolveErrorMessage(error, errorTexts), isError: true });
    } finally {
      setIsResending(false);
    }
  };

  return (
    <section className="verification-panel">
      <div className="verification-badge">{texts.verification.badge}</div>
      <p className="verification-copy">
        {texts.verification.instructions}{' '}
        <span className="verification-email">{email}</span>
      </p>
      <p className={`countdown ${secondsLeft === 0 ? 'expired' : ''}`}>
        {texts.verification.timerLabel} <span>{formatCountdown(secondsLeft)}</span>
      </p>
      {remainingAttempts != null && (
        <p className="verification-copy" data-testid="remaining-attempts">
          {texts.verification.attemptsLeft.replace('{count}', remainingAttempts)}
        </p>
      )}
      {resendLockSecondsLeft > 0 && (
        <p className="countdown expired">
          {texts.verification.resendLockLabel} <span>{formatCountdown(resendLockSecondsLeft)}</span>
        </p>
      )}

      <form className="form-grid" onSubmit={handleSubmit}>
        <div className="field-group">
          <VerificationCodeInput codeDigits={codeDigits} onChange={handleDigitChange} />
          <FieldError message={codeError} />
        </div>

        {status.text && (
          <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
            {status.text}
          </p>
        )}

        <div className="action-row">
          <button className="primary-button" type="submit" disabled={isLoading || remainingAttempts === 0}>
            {isLoading ? texts.buttons.verifying : texts.buttons.verify}
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

export default VerificationForm;
