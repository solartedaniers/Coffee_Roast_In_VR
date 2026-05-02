import React, { useState } from 'react';
import PasswordField from './PasswordField';
import { registerUser } from '../services/authService';
import { validateRegistrationForm } from '../utils/validation';

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

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((currentValue) => ({ ...currentValue, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const validationMessage = validateRegistrationForm(formData, texts);
    if (validationMessage) {
      setStatus({ text: validationMessage, isError: true });
      return;
    }

    setIsLoading(true);
    setStatus({ text: '', isError: false });

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
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      <label className="field-group">
        <span className="field-label">{texts.labels.fullName}</span>
        <input
          className="field-input"
          type="text"
          name="name"
          placeholder={texts.placeholders.fullName}
          value={formData.name}
          onChange={handleChange}
          required
        />
      </label>

      <label className="field-group">
        <span className="field-label">{texts.labels.email}</span>
        <input
          className="field-input"
          type="email"
          name="email"
          placeholder={texts.placeholders.email}
          value={formData.email}
          onChange={handleChange}
          required
        />
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
          required
        />
      </label>

      <PasswordField
        name="password"
        value={formData.password}
        onChange={handleChange}
        placeholder={texts.placeholders.password}
        label={texts.labels.password}
      />

      <PasswordField
        name="confirmPassword"
        value={formData.confirmPassword}
        onChange={handleChange}
        placeholder={texts.placeholders.confirmPassword}
        label={texts.labels.confirmPassword}
      />

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
