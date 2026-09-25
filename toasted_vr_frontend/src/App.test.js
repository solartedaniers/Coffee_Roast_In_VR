import { act, fireEvent, render, screen } from '@testing-library/react';
import App from './App';
import esTexts from './locals/es.json';
import { ACCOUNT_BLOCKED_EVENT, SESSION_REVOKED_EVENT } from './services/apiClient';
import { readSession, saveSession } from './services/sessionService';
import { confirmPasswordReset, loginUser, requestPasswordReset, verifyPasswordResetCode } from './services/authService';

jest.mock('./services/authService', () => ({
  ...jest.requireActual('./services/authService'),
  loginUser: jest.fn(),
  requestPasswordReset: jest.fn(),
  confirmPasswordReset: jest.fn(),
  verifyPasswordResetCode: jest.fn(),
}));

afterEach(() => {
  window.localStorage.clear();
});

test('renders entry experience title', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: /simulador vr/i })).toBeInTheDocument();
});

test('sends a blocked user back to the login with the blocked notice', () => {
  saveSession({
    accessToken: 'access',
    refreshToken: 'refresh',
    user: { id: 1, name: 'Ana', username: 'ana', role: 'PLAYER', knowledgeLevel: null }
  });
  render(<App />);

  act(() => {
    window.dispatchEvent(new Event(ACCOUNT_BLOCKED_EVENT));
  });

  expect(screen.getByText(esTexts.auth.errors.ACCOUNT_BLOCKED)).toBeInTheDocument();
  expect(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password)).toBeInTheDocument();
  expect(readSession()).toBeNull();
});

test('sends a user whose password changed elsewhere back to the login with the notice', () => {
  saveSession({
    accessToken: 'access',
    refreshToken: 'refresh',
    user: { id: 1, name: 'Ana', username: 'ana', role: 'PLAYER', knowledgeLevel: null }
  });
  render(<App />);

  act(() => {
    window.dispatchEvent(new Event(SESSION_REVOKED_EVENT));
  });

  expect(screen.getByText('Tu contraseña cambió. Inicia sesión de nuevo.')).toBeInTheDocument();
  expect(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password)).toBeInTheDocument();
  expect(readSession()).toBeNull();
});

test('takes an unverified user from the login to the verification screen ready to resend', async () => {
  loginUser.mockRejectedValue(Object.assign(new Error('Debes verificar tu correo antes de iniciar sesión.'), {
    code: 'EMAIL_NOT_VERIFIED',
  }));
  render(<App />);
  fireEvent.click(screen.getAllByRole('button', { name: esTexts.auth.entry.buttons.login })[0]);

  fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.email), {
    target: { value: 'pending@gmail.com' },
  });
  fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password), {
    target: { value: 'Password123' },
  });
  fireEvent.click(screen.getAllByRole('button', { name: esTexts.auth.login.buttons.submit }).at(-1));
  fireEvent.click(await screen.findByRole('button', { name: esTexts.auth.login.links.verifyAccount }));

  expect(screen.getByText('pending@gmail.com')).toBeInTheDocument();
  expect(screen.getByText('00:00')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: esTexts.auth.register.buttons.resend })).toBeEnabled();
});

// Correo -> código (el servidor lo valida) -> nueva contraseña -> login.
test('recovers the password from the login and returns to the login', async () => {
  const resetTexts = esTexts.auth.passwordReset;
  requestPasswordReset.mockResolvedValue({
    email: 'ana@gmail.com',
    codePolicy: { expiresInSeconds: 60, maxAttempts: 5, maxResends: 3, resendLockMinutes: 5 },
  });
  verifyPasswordResetCode.mockResolvedValue({ resetToken: 'reset-token', expiresInSeconds: 600 });
  confirmPasswordReset.mockResolvedValue({ message: 'ok' });
  render(<App />);
  fireEvent.click(screen.getAllByRole('button', { name: esTexts.auth.entry.buttons.login })[0]);

  fireEvent.click(screen.getByRole('button', { name: esTexts.auth.login.links.forgotPassword }));
  expect(screen.getByRole('heading', { name: resetTexts.title })).toBeInTheDocument();

  fireEvent.change(screen.getByPlaceholderText(resetTexts.placeholders.email), { target: { value: 'ana@gmail.com' } });
  fireEvent.click(screen.getByRole('button', { name: resetTexts.buttons.sendCode }));

  expect(await screen.findByRole('heading', { name: resetTexts.codeTitle })).toBeInTheDocument();
  expect(screen.getByText('ana@gmail.com')).toBeInTheDocument();
  expect(screen.queryByPlaceholderText(resetTexts.placeholders.newPassword)).not.toBeInTheDocument();

  '123456'.split('').forEach((digit, index) => {
    fireEvent.change(screen.getByLabelText(`Dígito ${index + 1} del código`), { target: { value: digit } });
  });
  fireEvent.click(screen.getByRole('button', { name: resetTexts.buttons.verifyCode }));

  expect(await screen.findByRole('heading', { name: resetTexts.resetTitle })).toBeInTheDocument();
  expect(verifyPasswordResetCode).toHaveBeenCalledWith({ email: 'ana@gmail.com', code: '123456' });
  fireEvent.change(screen.getByPlaceholderText(resetTexts.placeholders.newPassword), { target: { value: 'NewPassword9' } });
  fireEvent.change(screen.getByPlaceholderText(resetTexts.placeholders.confirmPassword), {
    target: { value: 'NewPassword9' },
  });
  fireEvent.click(screen.getByRole('button', { name: resetTexts.buttons.reset }));

  expect(await screen.findByText(resetTexts.success.text)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: resetTexts.buttons.goToLogin }));

  expect(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password)).toBeInTheDocument();
});
