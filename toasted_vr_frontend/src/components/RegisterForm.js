import React, { useState } from 'react';
import FieldError from './FieldError';
import PasswordField from './PasswordField';
import { registerUser } from '../services/authService';
import { useFieldErrors } from '../hooks/useFieldErrors';
import { getFieldErrors, hasFieldErrors } from '../utils/errorMessages';
import {
  isValidEmail,
  validateName,
  validateRegistrationEmail,
  validateRegistrationForm
} from '../utils/validation';

const initialFormData = {
  name: '',
  email: '',
  username: '',
  password: '',
  confirmPassword: ''
};

function RegisterForm({ texts, onRegistrationSuccess, onSwitchToLogin }) {
  const [formData, setFormData] = useState(initialFormData);
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);
  const { fieldErrors, setFieldErrors, clearFieldError, clearAllFieldErrors } = useFieldErrors();

  // Validación inmediata: el nombre se revisa en cada tecla; del correo solo
  // se avisa el dominio mal escrito mientras se escribe (el formato incompleto
  // se reporta al salir del campo, para no marcar error a mitad de escritura).
  const liveErrorFor = (name, value) => {
    if (name === 'name') {
      return validateName(value, texts);
    }
    if (name === 'email' && isValidEmail(value)) {
      return validateRegistrationEmail(value);
    }
    return '';
  };

  const showFieldError = (name, message) => {
    setFieldErrors((currentValue) => ({ ...currentValue, [name]: message }));
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((currentValue) => ({ ...currentValue, [name]: value }));

    const liveError = liveErrorFor(name, value);
    if (liveError) {
      showFieldError(name, liveError);
    } else {
      clearFieldError(name);
    }
  };

  const handleEmailBlur = () => {
    const message = formData.email ? validateRegistrationEmail(formData.email) : '';
    if (message) {
      showFieldError('email', message);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const validationErrors = validateRegistrationForm(formData, texts);
    if (hasFieldErrors(validationErrors)) {
      setFieldErrors(validationErrors);
      setStatus({ text: '', isError: false });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });
    clearAllFieldErrors();

    try {
      const response = await registerUser({
        name: formData.name,
        email: formData.email,
        username: formData.username,
        password: formData.password
      });

      setStatus({ text: response.message, isError: false });
      onRegistrationSuccess(response);
      setFormData(initialFormData);
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
    <form className="form-grid register-form" onSubmit={handleSubmit}>
      <div className="register-form-row">
        <label className="field-group">
          <span className="field-label">{texts.labels.fullName}</span>
          <input
            className="field-input"
            type="text"
            name="name"
            placeholder={texts.placeholders.fullName}
            value={formData.name}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.name)}
            required
          />
          <FieldError message={fieldErrors.name} />
        </label>

        <label className="field-group">
          <span className="field-label">{texts.labels.username}</span>
          <input
            className="field-input"
            type="text"
            name="username"
            placeholder={texts.placeholders.username}
            value={formData.username}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.username)}
            required
          />
          <FieldError message={fieldErrors.username} />
        </label>
      </div>

      <label className="field-group">
        <span className="field-label">{texts.labels.email}</span>
        <input
          className="field-input"
          type="email"
          name="email"
          placeholder={texts.placeholders.email}
          value={formData.email}
          onChange={handleChange}
          onBlur={handleEmailBlur}
          aria-invalid={Boolean(fieldErrors.email)}
          required
        />
        <FieldError message={fieldErrors.email} />
      </label>

      <div className="register-form-row">
        <PasswordField
          name="password"
          value={formData.password}
          onChange={handleChange}
          placeholder={texts.placeholders.password}
          label={texts.labels.password}
          error={fieldErrors.password}
        />

        <PasswordField
          name="confirmPassword"
          value={formData.confirmPassword}
          onChange={handleChange}
          placeholder={texts.placeholders.confirmPassword}
          label={texts.labels.confirmPassword}
          error={fieldErrors.confirmPassword}
        />
      </div>

      {status.text && (
        <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
          {status.text}
        </p>
      )}

      <button className="primary-button" type="submit" disabled={isLoading}>
        {isLoading ? texts.buttons.loading : texts.buttons.submit}
      </button>

      <div className="form-links">
        <button type="button" className="text-link text-link-strong" onClick={onSwitchToLogin}>
          {texts.links.login}
        </button>
      </div>
    </form>
  );
}

export default RegisterForm;
