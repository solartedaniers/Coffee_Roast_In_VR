import React, { useState } from 'react';
import PasswordField from './PasswordField';
import { loginUser } from '../services/authService';

const initialCredentials = {
  identifier: '',
  password: ''
};

function LoginForm({ texts, onLoginSuccess }) {
  const [credentials, setCredentials] = useState(initialCredentials);
  const [status, setStatus] = useState({ text: '', isError: false });
  const [isLoading, setIsLoading] = useState(false);

  const handleChange = (event) => {
    const { name, value } = event.target;
    setCredentials((currentValue) => ({ ...currentValue, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setIsLoading(true);
    setStatus({ text: '', isError: false });

    try {
      const response = await loginUser(credentials);
      setCredentials(initialCredentials);
      onLoginSuccess(response);
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      <label className="field-group">
        <span className="field-label">{texts.labels.identifier}</span>
        <input
          className="field-input"
          type="text"
          name="identifier"
          placeholder={texts.placeholders.identifier}
          value={credentials.identifier}
          onChange={handleChange}
          required
        />
      </label>

      <PasswordField
        name="password"
        value={credentials.password}
        onChange={handleChange}
        placeholder={texts.placeholders.password}
        label={texts.labels.password}
      />

      {status.text && (
        <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
          {status.text}
        </p>
      )}

      <button className="primary-button" type="submit" disabled={isLoading}>
        {isLoading ? texts.buttons.loading : texts.buttons.submit}
      </button>
    </form>
  );
}

export default LoginForm;
