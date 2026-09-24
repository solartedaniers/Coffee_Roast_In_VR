import React, { useState } from 'react';
import FieldError from './FieldError';
import PasswordField from './PasswordField';
import { loginUser } from '../services/authService';
import { useFieldErrors } from '../hooks/useFieldErrors';
import { getFieldErrors, hasFieldErrors, resolveErrorMessage } from '../utils/errorMessages';
import { validateEmail } from '../utils/validation';

const initialCredentials = {
  email: '',
  password: ''
};

function LoginForm({ texts, errorTexts, notice, onLoginSuccess, onSwitchToRegister, onVerifyAccount }) {
  const [credentials, setCredentials] = useState(initialCredentials);
  const [status, setStatus] = useState({ text: notice || '', isError: Boolean(notice) });
  const [isLoading, setIsLoading] = useState(false);
  // Correo de una cuenta sin verificar, para ofrecer "Verificar mi cuenta".
  const [unverifiedEmail, setUnverifiedEmail] = useState('');
  const { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors } = useFieldErrors();

  const handleChange = (event) => {
    const { name, value } = event.target;
    setCredentials((currentValue) => ({ ...currentValue, [name]: value }));
    clearFieldError(name);
  };

  const handleForgotPassword = () => {
    setStatus({ text: texts.messages.forgotPasswordUnavailable, isError: true });
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setUnverifiedEmail('');

    const emailMessage = validateEmail(credentials.email);
    if (emailMessage) {
      setFieldErrors({ email: emailMessage });
      setStatus({ text: '', isError: false });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();

    try {
      const response = await loginUser(credentials);
      setCredentials(initialCredentials);
      onLoginSuccess(response);
    } catch (error) {
      const apiFieldErrors = getFieldErrors(error);
      if (hasFieldErrors(apiFieldErrors)) {
        setFieldErrors(apiFieldErrors);
      } else {
        setStatus({ text: resolveErrorMessage(error, errorTexts), isError: true });
      }

      if (error.code === 'EMAIL_NOT_VERIFIED') {
        setUnverifiedEmail(credentials.email.trim());
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
          value={credentials.email}
          onChange={handleChange}
          aria-invalid={Boolean(fieldErrors.email)}
          required
        />
        <FieldError message={fieldErrors.email} />
      </label>

      <PasswordField
        name="password"
        value={credentials.password}
        onChange={handleChange}
        placeholder={texts.placeholders.password}
        label={texts.labels.password}
        error={fieldErrors.password}
      />

      {status.text && (
        <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
          {status.text}
        </p>
      )}

      {unverifiedEmail && onVerifyAccount && (
        <button type="button" className="text-link text-link-strong" onClick={() => onVerifyAccount(unverifiedEmail)}>
          {texts.links.verifyAccount}
        </button>
      )}

      <button className="primary-button" type="submit" disabled={isLoading}>
        {isLoading ? texts.buttons.loading : texts.buttons.submit}
      </button>

      <div className="form-links">
        <button type="button" className="text-link" onClick={handleForgotPassword}>
          {texts.links.forgotPassword}
        </button>
        <button type="button" className="text-link text-link-strong" onClick={onSwitchToRegister}>
          {texts.links.createAccount}
        </button>
      </div>
    </form>
  );
}

export default LoginForm;
