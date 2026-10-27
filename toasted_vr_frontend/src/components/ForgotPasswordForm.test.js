import { fireEvent, render, screen } from '@testing-library/react';
import ForgotPasswordForm from './ForgotPasswordForm';
import esTexts from '../locals/es.json';
import { requestPasswordReset } from '../services/authService';

jest.mock('../services/authService', () => ({
  requestPasswordReset: jest.fn(),
}));

const texts = esTexts.auth.passwordReset;
const codePolicy = { expiresInSeconds: 60, maxAttempts: 5, maxResends: 3, resendLockMinutes: 5 };

const typeEmail = (value) =>
  fireEvent.change(screen.getByPlaceholderText(texts.placeholders.email), { target: { value } });

describe('ForgotPasswordForm', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows an invalid email under the field without calling the API', () => {
    render(<ForgotPasswordForm texts={texts} onCodeRequested={jest.fn()} />);

    typeEmail('a@gmailcom');
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.sendCode }));

    expect(screen.getByRole('alert')).toHaveTextContent(esTexts.auth.validation.invalidEmail);
    expect(requestPasswordReset).not.toHaveBeenCalled();

    typeEmail('a@gmail.com');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('moves to the next step with the email and the code policy', async () => {
    const onCodeRequested = jest.fn();
    requestPasswordReset.mockResolvedValue({ message: 'genérico', email: 'ana@gmail.com', codePolicy });
    render(<ForgotPasswordForm texts={texts} onCodeRequested={onCodeRequested} />);

    typeEmail(' ana@gmail.com ');
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.sendCode }));

    await screen.findByRole('button', { name: texts.buttons.sendCode });
    expect(requestPasswordReset).toHaveBeenCalledWith({ email: 'ana@gmail.com' });
    expect(onCodeRequested).toHaveBeenCalledWith({ email: 'ana@gmail.com', codePolicy });
  });
});
