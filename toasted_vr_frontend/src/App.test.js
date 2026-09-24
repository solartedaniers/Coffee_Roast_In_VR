import { act, fireEvent, render, screen } from '@testing-library/react';
import App from './App';
import esTexts from './locals/es.json';
import { ACCOUNT_BLOCKED_EVENT } from './services/apiClient';
import { readSession, saveSession } from './services/sessionService';
import { loginUser } from './services/authService';

jest.mock('./services/authService', () => ({
  ...jest.requireActual('./services/authService'),
  loginUser: jest.fn(),
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
