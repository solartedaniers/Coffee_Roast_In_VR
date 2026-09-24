import { act, fireEvent, render, screen } from '@testing-library/react';
import VerificationForm from './VerificationForm';
import esTexts from '../locals/es.json';
import { resendVerificationCode, verifyEmailCode } from '../services/authService';

jest.mock('../services/authService', () => ({
  verifyEmailCode: jest.fn(),
  resendVerificationCode: jest.fn(),
}));

const texts = esTexts.auth.register;
const codePolicy = { expiresInSeconds: 60, maxAttempts: 5, maxResends: 3, resendLockMinutes: 5 };

const renderForm = () => render(
  <VerificationForm
    email="ana@toastedvr.test"
    codePolicy={codePolicy}
    texts={texts}
    errorTexts={esTexts.auth.errors}
    onVerificationSuccess={jest.fn()}
  />
);

const apiError = (code, details = null) => Object.assign(new Error('mensaje del backend'), { code, details });

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

const resendButton = () => screen.queryByRole('button', { name: texts.buttons.resend });

describe('VerificationForm', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  test('keeps the countdown with the configured duration and shows the resend button at zero', () => {
    renderForm();

    expect(screen.getByText('01:00')).toBeInTheDocument();
    expect(resendButton()).not.toBeInTheDocument();

    advanceSeconds(60);

    expect(screen.getByText('00:00')).toBeInTheDocument();
    expect(resendButton()).toBeEnabled();
  });

  test('shows only the current number of remaining attempts', async () => {
    verifyEmailCode.mockRejectedValue(apiError('INVALID_CODE', { remainingAttempts: 4 }));
    renderForm();
    expect(screen.getByTestId('remaining-attempts')).toHaveTextContent(/^Intentos restantes: 5$/);

    typeCode('123456');
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.verify }));

    expect(await screen.findByText(esTexts.auth.errors.INVALID_CODE)).toBeInTheDocument();
    const attemptsText = screen.getByTestId('remaining-attempts').textContent;
    expect(attemptsText).toBe('Intentos restantes: 4');
    expect(attemptsText.match(/\d+/g)).toEqual(['4']);
  });

  test('enables the resend button right away when the attempts are exhausted', async () => {
    verifyEmailCode.mockRejectedValue(apiError('CODE_ATTEMPTS_EXHAUSTED'));
    renderForm();

    typeCode('123456');
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.verify }));

    expect(await screen.findByText(esTexts.auth.errors.CODE_ATTEMPTS_EXHAUSTED)).toBeInTheDocument();
    expect(resendButton()).toBeEnabled();
    expect(screen.getByRole('button', { name: texts.buttons.verify })).toBeDisabled();
  });

  test('restarts the countdown and the attempts after a successful resend', async () => {
    resendVerificationCode.mockResolvedValue({ codePolicy });
    renderForm();
    advanceSeconds(60);

    fireEvent.click(resendButton());

    expect(await screen.findByText(texts.verification.codeResent)).toBeInTheDocument();
    expect(screen.getByText('01:00')).toBeInTheDocument();
    expect(resendButton()).not.toBeInTheDocument();
    expect(resendVerificationCode).toHaveBeenCalledWith({ email: 'ana@toastedvr.test' });
  });

  test('shows "try later" with a lock countdown after too many resends', async () => {
    resendVerificationCode.mockRejectedValue(apiError('TOO_MANY_RESENDS', { secondsRemaining: 300 }));
    renderForm();
    advanceSeconds(60);

    fireEvent.click(resendButton());

    expect(await screen.findByText(esTexts.auth.errors.TOO_MANY_RESENDS)).toBeInTheDocument();
    expect(screen.getByText('05:00')).toBeInTheDocument();
    expect(resendButton()).toBeDisabled();

    advanceSeconds(300);

    expect(resendButton()).toBeEnabled();
  });
});
