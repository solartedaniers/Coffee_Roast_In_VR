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

  test('rejects an email without a dot in the domain before calling the API', () => {
    renderLoginForm();

    fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.email), {
      target: { value: 'a@gmailcom' },
    });
    fireEvent.change(screen.getByPlaceholderText(esTexts.auth.login.placeholders.password), {
      target: { value: 'Password123!' },
    });
    fireEvent.submit(screen.getByRole('button', { name: esTexts.auth.login.buttons.submit }).closest('form'));

    expect(screen.getByText(esTexts.auth.validation.invalidEmail)).toBeInTheDocument();
    expect(loginUser).not.toHaveBeenCalled();
  });

  test('shows the notice received when the user was expelled', () => {
    renderLoginForm({ notice: esTexts.auth.errors.ACCOUNT_BLOCKED });

    expect(screen.getByText(esTexts.auth.errors.ACCOUNT_BLOCKED)).toBeInTheDocument();
  });
});
