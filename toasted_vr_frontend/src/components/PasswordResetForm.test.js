import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PasswordResetForm from './PasswordResetForm';
import esTexts from '../locals/es.json';
import { confirmPasswordReset, requestPasswordReset } from '../services/authService';

jest.mock('../services/authService', () => ({
  confirmPasswordReset: jest.fn(),
  requestPasswordReset: jest.fn(),
}));

const texts = esTexts.auth.passwordReset;
const codePolicy = { expiresInSeconds: 60, maxAttempts: 5, maxResends: 3, resendLockMinutes: 5 };

const renderForm = (onResetSuccess = jest.fn()) => render(
  <PasswordResetForm
    email="ana@gmail.com"
    codePolicy={codePolicy}
    texts={texts}
    errorTexts={esTexts.auth.errors}
    onResetSuccess={onResetSuccess}
  />
);

const advanceSeconds = (seconds) => {
  for (let second = 0; second < seconds; second++) {
    act(() => {
      jest.advanceTimersByTime(1000);
    });
  }
};

const typeCode = (code) => {
  code.split('').forEach((digit, index) => {
    fireEvent.change(screen.getByLabelText(`Dígito ${index + 1} del código`), { target: { value: digit } });
  });
};

const typePasswords = (newPassword, confirmPassword) => {
  fireEvent.change(screen.getByPlaceholderText(texts.placeholders.newPassword), { target: { value: newPassword } });
  fireEvent.change(screen.getByPlaceholderText(texts.placeholders.confirmPassword), {
    target: { value: confirmPassword },
  });
};

const submit = () => fireEvent.click(screen.getByRole('button', { name: texts.buttons.reset }));
const resendButton = () => screen.queryByRole('button', { name: texts.buttons.resend });
const apiError = (code, details = null) => Object.assign(new Error('mensaje del backend'), { code, details });

describe('PasswordResetForm', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    jest.clearAllTimers();
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  test('reuses the countdown with the configured duration and shows resend at zero', () => {
    renderForm();

    expect(screen.getByText('01:00')).toBeInTheDocument();
    expect(resendButton()).not.toBeInTheDocument();

    advanceSeconds(60);

    expect(resendButton()).toBeEnabled();
  });

  test('shows local errors under the code and the confirmation without calling the API', () => {
    renderForm();

    typePasswords('NewPassword9', 'Different9');
    submit();

    const alerts = screen.getAllByRole('alert').map((alert) => alert.textContent);
    expect(alerts).toEqual([texts.messages.invalidCodeLength, texts.messages.passwordMismatch]);
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  test('sends the code and passwords and reports success', async () => {
    const onResetSuccess = jest.fn();
    confirmPasswordReset.mockResolvedValue({ message: 'ok' });
    renderForm(onResetSuccess);

    typeCode('123456');
    typePasswords('NewPassword9', 'NewPassword9');
    submit();

    await waitFor(() => expect(onResetSuccess).toHaveBeenCalled());
    expect(confirmPasswordReset).toHaveBeenCalledWith({
      email: 'ana@gmail.com',
      code: '123456',
      newPassword: 'NewPassword9',
      confirmPassword: 'NewPassword9',
    });
  });

  test('shows a rejected code under the code field', async () => {
    confirmPasswordReset.mockRejectedValue(apiError('INVALID_CODE'));
    renderForm();

    typeCode('123456');
    typePasswords('NewPassword9', 'NewPassword9');
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent(texts.messages.invalidCode);
  });

  test('shows backend password errors under the new password field', async () => {
    confirmPasswordReset.mockRejectedValue(apiError('VALIDATION_ERROR', {
      fieldErrors: { newPassword: 'La contraseña debe tener entre 8 y 72 caracteres.' },
    }));
    renderForm();

    typeCode('123456');
    typePasswords('weakpass', 'weakpass');
    submit();

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña debe tener entre 8 y 72 caracteres.');
  });

  test('restarts the countdown after a resend', async () => {
    requestPasswordReset.mockResolvedValue({ codePolicy });
    renderForm();
    advanceSeconds(60);

    fireEvent.click(resendButton());

    expect(await screen.findByText(texts.messages.codeResent)).toBeInTheDocument();
    expect(screen.getByText('01:00')).toBeInTheDocument();
    expect(requestPasswordReset).toHaveBeenCalledWith({ email: 'ana@gmail.com' });
  });

  test('locks resends for five minutes after three resends without calling the API again', async () => {
    requestPasswordReset.mockResolvedValue({ codePolicy });
    renderForm();

    for (let resend = 0; resend < 3; resend++) {
      advanceSeconds(60);
      fireEvent.click(resendButton());
      await screen.findByText('01:00');
    }

    advanceSeconds(60);
    fireEvent.click(resendButton());

    expect(screen.getByText(esTexts.auth.errors.TOO_MANY_RESENDS)).toBeInTheDocument();
    expect(screen.getByText('05:00')).toBeInTheDocument();
    expect(resendButton()).toBeDisabled();
    expect(requestPasswordReset).toHaveBeenCalledTimes(3);

    advanceSeconds(300);
    expect(resendButton()).toBeEnabled();
  });
});
