import { act, render, screen } from '@testing-library/react';
import App from './App';
import esTexts from './locals/es.json';
import { ACCOUNT_BLOCKED_EVENT } from './services/apiClient';
import { readSession, saveSession } from './services/sessionService';

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
