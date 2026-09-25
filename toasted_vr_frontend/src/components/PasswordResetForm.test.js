import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PasswordResetForm from './PasswordResetForm';
import esTexts from '../locals/es.json';
import { confirmPasswordReset, requestPasswordReset, verifyPasswordResetCode } from '../services/authService';

jest.mock('../services/authService', () => ({
  confirmPasswordReset: jest.fn(),
  requestPasswordReset: jest.fn(),
  verifyPasswordResetCode: jest.fn(),
}));

const texts = esTexts.auth.passwordReset;
const codePolicy = { expiresInSeconds: 60, maxAttempts: 5, maxResends: 3, resendLockMinutes: 5 };

const renderForm = (handlers = {}) => {
  const props = {
    onCodeVerified: jest.fn(),
    onRestart: jest.fn(),
    onResetSuccess: jest.fn(),
    ...handlers,
  };
  render(
    <PasswordResetForm
      email="ana@gmail.com"
      codePolicy={codePolicy}
      texts={texts}
      errorTexts={esTexts.auth.errors}
      {...props}
    />
  );
  return props;
};

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

const verifyButton = () => screen.getByRole('button', { name: texts.buttons.verifyCode });
const submitPasswords = () => fireEvent.click(screen.getByRole('button', { name: texts.buttons.reset }));
const resendButton = () => screen.queryByRole('button', { name: texts.buttons.resend });
const newPasswordField = () => screen.queryByPlaceholderText(texts.placeholders.newPassword);
const apiError = (code, details = null, message = 'mensaje del backend') =>
  Object.assign(new Error(message), { code, details });

// Deja el formulario en el paso de la nueva contraseña.
const passCodeStep = async () => {
  verifyPasswordResetCode.mockResolvedValue({ resetToken: 'reset-token', expiresInSeconds: 600 });
  typeCode('123456');
  fireEvent.click(verifyButton());
  await screen.findByPlaceholderText(texts.placeholders.newPassword);
};

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

  test('asks only for the code first, without password fields', () => {
    renderForm();

    expect(verifyButton()).toBeInTheDocument();
    expect(newPasswordField()).not.toBeInTheDocument();
    expect(screen.queryByPlaceholderText(texts.placeholders.confirmPassword)).not.toBeInTheDocument();
  });

  test('shows an incomplete code under the code field without calling the API', () => {
    renderForm();

    typeCode('123');
    fireEvent.click(verifyButton());

    expect(screen.getByRole('alert')).toHaveTextContent(texts.messages.invalidCodeLength);
    expect(verifyPasswordResetCode).not.toHaveBeenCalled();
  });

  test('keeps the user on the code step when the server rejects the code', async () => {
    verifyPasswordResetCode.mockRejectedValue(apiError('INVALID_CODE'));
    const { onCodeVerified } = renderForm();

    typeCode('123456');
    fireEvent.click(verifyButton());

    expect(await screen.findByRole('alert')).toHaveTextContent(texts.messages.invalidCode);
    expect(newPasswordField()).not.toBeInTheDocument();
    expect(onCodeVerified).not.toHaveBeenCalled();
  });

  test('shows the new password step only after the server validates the code', async () => {
    const { onCodeVerified } = renderForm();

    await passCodeStep();

    expect(verifyPasswordResetCode).toHaveBeenCalledWith({ email: 'ana@gmail.com', code: '123456' });
    expect(onCodeVerified).toHaveBeenCalled();
    expect(screen.getByText(texts.codeVerifiedBadge)).toBeInTheDocument();
    expect(screen.queryByLabelText('Dígito 1 del código')).not.toBeInTheDocument();
  });

  test('sends the reset permission with the new password and reports success', async () => {
    confirmPasswordReset.mockResolvedValue({ message: 'ok' });
    const { onResetSuccess } = renderForm();
    await passCodeStep();

    typePasswords('NewPassword9', 'NewPassword9');
    submitPasswords();

    await waitFor(() => expect(onResetSuccess).toHaveBeenCalled());
    expect(confirmPasswordReset).toHaveBeenCalledWith({
      resetToken: 'reset-token',
      newPassword: 'NewPassword9',
      confirmPassword: 'NewPassword9',
    });
  });

  test('shows a password mismatch under the confirmation without calling the API', async () => {
    renderForm();
    await passCodeStep();

    typePasswords('NewPassword9', 'Different9');
    submitPasswords();

    expect(screen.getByRole('alert')).toHaveTextContent(texts.messages.passwordMismatch);
    expect(confirmPasswordReset).not.toHaveBeenCalled();
  });

  test('shows backend password errors, such as reusing the current one, under the new password field', async () => {
    const sameAsCurrent = 'La nueva contraseña no puede ser igual a la actual.';
    confirmPasswordReset.mockRejectedValue(apiError('VALIDATION_ERROR', { fieldErrors: { newPassword: sameAsCurrent } }));
    renderForm();
    await passCodeStep();

    typePasswords('Password123', 'Password123');
    submitPasswords();

    expect(await screen.findByRole('alert')).toHaveTextContent(sameAsCurrent);
  });

  test('offers a new code when the reset permission expired', async () => {
    const expired = 'El tiempo para cambiar la contraseña venció. Solicita un código nuevo.';
    confirmPasswordReset.mockRejectedValue(apiError('CODE_EXPIRED', null, expired));
    const { onRestart } = renderForm();
    await passCodeStep();

    typePasswords('NewPassword9', 'NewPassword9');
    submitPasswords();

    expect(await screen.findByText(expired)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.requestNewCode }));
    expect(onRestart).toHaveBeenCalled();
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
