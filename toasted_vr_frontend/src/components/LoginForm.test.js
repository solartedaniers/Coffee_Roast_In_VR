import { fireEvent, render, screen } from '@testing-library/react';
import LoginForm from './LoginForm';
import esTexts from '../locals/es.json';
import { loginUser } from '../services/authService';

jest.mock('../services/authService', () => ({
  loginUser: jest.fn(),
}));

const renderLoginForm = (props = {}) => render(
  <LoginForm
    texts={esTexts.auth.login}
    errorTexts={esTexts.auth.errors}
    onLoginSuccess={jest.fn()}
    onSwitchToRegister={jest.fn()}
    {...props}
  />
);

const submitCredentials = () => {
  fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.email), {
    target: { value: 'player@toastedvr.test' },
  });
  fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password), {
    target: { value: 'Password123!' },
  });
  fireEvent.click(screen.getByRole('button', { name: esTexts.auth.login.buttons.submit }));
};

describe('LoginForm', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows the blocked-account text from es.json when the backend reports ACCOUNT_BLOCKED', async () => {
    loginUser.mockRejectedValue(Object.assign(new Error('mensaje del backend'), { code: 'ACCOUNT_BLOCKED' }));
    renderLoginForm();

    submitCredentials();

    expect(await screen.findByText(esTexts.auth.errors.ACCOUNT_BLOCKED)).toBeInTheDocument();
  });

  test('falls back to the backend message for errors without a known code', async () => {
    loginUser.mockRejectedValue(
      Object.assign(new Error('Las credenciales ingresadas no son válidas.'), { code: 'AUTHENTICATION_FAILED' })
    );
    renderLoginForm();

    submitCredentials();

    expect(await screen.findByText('Las credenciales ingresadas no son válidas.')).toBeInTheDocument();
  });

  test('shows an invalid email under the email field and clears it when the user corrects it', () => {
    renderLoginForm();
    const emailInput = screen.getByPlaceholderText(esTexts.auth.login.placeholders.email);

    fireEvent.change(emailInput, { target: { value: 'a@gmailcom' } });
    fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password), {
      target: { value: 'Password123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: esTexts.auth.login.buttons.submit }));

    expect(screen.getByRole('alert')).toHaveTextContent(esTexts.auth.validation.invalidEmail);
    expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText(esTexts.auth.validation.invalidEmail, { selector: '.status-message' })).not.toBeInTheDocument();
    expect(loginUser).not.toHaveBeenCalled();

    fireEvent.change(emailInput, { target: { value: 'a@gmail.com' } });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('shows backend field errors under their fields instead of the general box', async () => {
    loginUser.mockRejectedValue(Object.assign(new Error('La contraseña es obligatoria.'), {
      code: 'VALIDATION_ERROR',
      details: { fieldErrors: { password: 'La contraseña es obligatoria.' } },
    }));
    renderLoginForm();

    submitCredentials();

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña es obligatoria.');
    expect(screen.queryByText('La contraseña es obligatoria.', { selector: '.status-message' })).not.toBeInTheDocument();
  });

  test('offers "Verificar mi cuenta" when the email is not verified', async () => {
    const onVerifyAccount = jest.fn();
    loginUser.mockRejectedValue(Object.assign(new Error('Debes verificar tu correo antes de iniciar sesión.'), {
      code: 'EMAIL_NOT_VERIFIED',
    }));
    renderLoginForm({ onVerifyAccount });

    submitCredentials();
    fireEvent.click(await screen.findByRole('button', { name: esTexts.auth.login.links.verifyAccount }));

    expect(onVerifyAccount).toHaveBeenCalledWith('player@toastedvr.test');
  });

  test('does not offer account verification for other errors', async () => {
    loginUser.mockRejectedValue(Object.assign(new Error('Credenciales'), { code: 'AUTHENTICATION_FAILED' }));
    renderLoginForm({ onVerifyAccount: jest.fn() });

    submitCredentials();

    await screen.findByText('Credenciales');
    expect(screen.queryByRole('button', { name: esTexts.auth.login.links.verifyAccount })).not.toBeInTheDocument();
  });

  test('opens password recovery from the forgot password link', () => {
    const onForgotPassword = jest.fn();
    renderLoginForm({ onForgotPassword });

    fireEvent.click(screen.getByRole('button', { name: esTexts.auth.login.links.forgotPassword }));

    expect(onForgotPassword).toHaveBeenCalled();
  });

  test('shows the notice received when the user was expelled', () => {
    renderLoginForm({ notice: esTexts.auth.errors.ACCOUNT_BLOCKED });

    expect(screen.getByText(esTexts.auth.errors.ACCOUNT_BLOCKED)).toBeInTheDocument();
  });
});
