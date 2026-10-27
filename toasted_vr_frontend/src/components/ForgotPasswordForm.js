import React, { useState } from 'react';
import FieldError from './FieldError';
import { requestPasswordReset } from '../services/authService';
import { useFieldErrors } from '../hooks/useFieldErrors';
import { getFieldErrors, hasFieldErrors } from '../utils/errorMessages';
import { validateEmail } from '../utils/validation';

// Primer paso de la recuperación: pedir el código. La respuesta del backend es
// la misma exista o no la cuenta, así que siempre se avanza al segundo paso.
function ForgotPasswordForm({ texts, onCodeRequested }) {
  const [email, setEmail] = useState('');
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);
  const { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors } = useFieldErrors();

  const handleChange = (event) => {
    setEmail(event.target.value);
    clearFieldError('email');
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const emailMessage = validateEmail(email);
    if (emailMessage) {
      setFieldErrors({ email: emailMessage });
      setStatus({ text: '', isError: false });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();

    try {
      const response = await requestPasswordReset({ email: email.trim() });
      onCodeRequested({ email: response.email, codePolicy: response.codePolicy });
    } catch (error) {
      const apiFieldErrors = getFieldErrors(error);
      if (hasFieldErrors(apiFieldErrors)) {
        setFieldErrors(apiFieldErrors);
      } else {
        setStatus({ text: error.message, isError: true });
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      <label className="field-group">
        <span className="field-label">{texts.labels.email}</span>
        <input
          className="field-input"
          type="email"
          name="email"
          placeholder={texts.placeholders.email}
          value={email}
          onChange={handleChange}
          aria-invalid={Boolean(fieldErrors.email)}
          required
        />
        <FieldError message={fieldErrors.email} />
      </label>

      {status.text && (
        <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
          {status.text}
        </p>
      )}

      <button className="primary-button" type="submit" disabled={isLoading}>
        {isLoading ? texts.buttons.sending : texts.buttons.sendCode}
      </button>
    </form>
  );
}

export default ForgotPasswordForm;
