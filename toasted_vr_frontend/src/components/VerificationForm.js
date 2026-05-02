import React, { useEffect, useMemo, useState } from 'react';
import VerificationCodeInput from './VerificationCodeInput';
import { verifyEmailCode } from '../services/authService';

function VerificationForm({ email, expiresInMinutes, texts, onVerificationSuccess }) {
  const [codeDigits, setCodeDigits] = useState(['', '', '', '', '', '']);
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(expiresInMinutes * 60);

  const verificationCode = useMemo(() => codeDigits.join(''), [codeDigits]);
  const formattedTime = useMemo(() => {
    const minutes = Math.floor(secondsLeft / 60);
    const seconds = secondsLeft % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }, [secondsLeft]);

  useEffect(() => {
    setSecondsLeft(expiresInMinutes * 60);
  }, [expiresInMinutes, email]);

  useEffect(() => {
    if (secondsLeft <= 0) {
      return undefined;
    }

    const timerId = window.setInterval(() => {
      setSecondsLeft((currentValue) => (currentValue > 0 ? currentValue - 1 : 0));
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [secondsLeft]);

  const handleDigitChange = (index, value) => {
    setCodeDigits((currentValue) =>
      currentValue.map((digit, digitIndex) => (digitIndex === index ? value : digit))
    );
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (verificationCode.length !== 6) {
      setStatus({ text: texts.messages.invalidCodeLength, isError: true });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });

    try {
      const response = await verifyEmailCode({ email, code: verificationCode });
      setStatus({ text: response.message, isError: false });
      onVerificationSuccess(response);
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsLoading(false);
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
        {texts.verification.timerLabel} <span>{formattedTime}</span>
      </p>

      <form className="form-grid" onSubmit={handleSubmit}>
        <VerificationCodeInput codeDigits={codeDigits} onChange={handleDigitChange} />

        {status.text && (
          <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
            {status.text}
          </p>
        )}

        <div className="action-row">
          <button className="primary-button" type="submit" disabled={isLoading}>
            {isLoading ? texts.buttons.verifying : texts.buttons.verify}
          </button>
        </div>
      </form>
    </section>
  );
}

export default VerificationForm;
