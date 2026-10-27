import { fireEvent, render, screen } from '@testing-library/react';
import RegisterForm from './RegisterForm';
import esTexts from '../locals/es.json';
import { registerUser } from '../services/authService';

jest.mock('../services/authService', () => ({
  registerUser: jest.fn(),
}));

const texts = esTexts.auth.register;

const renderRegisterForm = () => render(
  <RegisterForm texts={texts} onRegistrationSuccess={jest.fn()} onSwitchToLogin={jest.fn()} />
);

const fillForm = (values) => {
  const fields = {
    name: texts.placeholders.fullName,
    email: texts.placeholders.email,
    username: texts.placeholders.username,
    password: texts.placeholders.password,
    confirmPassword: texts.placeholders.confirmPassword,
  };

  Object.entries(values).forEach(([field, value]) => {
    fireEvent.change(screen.getByPlaceholderText(fields[field]), { target: { value } });
  });
};

const validValues = {
  name: 'Ana Torres',
  email: 'ana@gmail.com',
  username: 'anaTorres',
  password: 'Password123',
  confirmPassword: 'Password123',
};

describe('RegisterForm', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows every local error under its own field without calling the API', () => {
    renderRegisterForm();
    fillForm({ ...validValues, email: 'a@gmailcom', confirmPassword: 'Different123' });

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    const alerts = screen.getAllByRole('alert').map((alert) => alert.textContent);
    expect(alerts).toEqual([esTexts.auth.validation.invalidEmail, texts.messages.passwordMismatch]);
    expect(screen.getByPlaceholderText(texts.placeholders.email)).toHaveAttribute('aria-invalid', 'true');
    expect(registerUser).not.toHaveBeenCalled();
  });

  test('clears only the error of the field the user corrects', () => {
    renderRegisterForm();
    fillForm({ ...validValues, email: 'a@gmailcom', confirmPassword: 'Different123' });
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    fillForm({ email: 'a@gmail.com' });

    const alerts = screen.getAllByRole('alert').map((alert) => alert.textContent);
    expect(alerts).toEqual([texts.messages.passwordMismatch]);
  });

  test('shows backend field errors, such as a taken email, under their fields', async () => {
    registerUser.mockRejectedValue(Object.assign(new Error('El correo electrónico ya está registrado.'), {
      code: 'CONFLICT',
      details: { fieldErrors: { email: 'El correo electrónico ya está registrado.' } },
    }));
    renderRegisterForm();
    fillForm(validValues);

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El correo electrónico ya está registrado.');
    expect(
      screen.queryByText('El correo electrónico ya está registrado.', { selector: '.status-message' })
    ).not.toBeInTheDocument();
  });

  test('warns immediately in red when the name gets a number or symbol, and clears it when fixed', () => {
    renderRegisterForm();

    fillForm({ name: 'Ana 2' });
    expect(screen.getByRole('alert')).toHaveTextContent(texts.messages.nameLettersOnly);
    expect(screen.getByRole('alert')).toHaveClass('field-error');
    expect(screen.getByPlaceholderText(texts.placeholders.fullName)).toHaveAttribute('aria-invalid', 'true');

    fillForm({ name: 'Ana Torres' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  test('warns immediately about a misspelled email domain while typing', () => {
    renderRegisterForm();

    fillForm({ email: 'ana@gail.com' });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Correo inexistente: el dominio «gail.com» no existe. ¿Quisiste decir ana@gmail.com?'
    );
  });

  test('does not flag an unfinished email until the user leaves the field', () => {
    renderRegisterForm();

    fillForm({ email: 'ana@gma' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.blur(screen.getByPlaceholderText(texts.placeholders.email));
    expect(screen.getByRole('alert')).toHaveTextContent(esTexts.auth.validation.invalidEmail);
  });

  test('does not register with a bad name or a misspelled domain', () => {
    renderRegisterForm();
    fillForm({ ...validValues, name: 'Ana_Torres', email: 'ana@hotmial.com' });

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(registerUser).not.toHaveBeenCalled();
  });

  test('lets the username use letters, numbers and symbols', async () => {
    registerUser.mockResolvedValue({ message: 'ok' });
    renderRegisterForm();
    fillForm({ ...validValues, username: 'Ana_2024#!' });

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    expect(await screen.findByText('ok')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(registerUser).toHaveBeenCalledWith(expect.objectContaining({ username: 'Ana_2024#!' }));
  });

  test('shows the server message when the email domain does not exist', async () => {
    const notFound = 'Correo inexistente: el dominio «noexiste.com» no existe. Revisa tu correo.';
    registerUser.mockRejectedValue(Object.assign(new Error(notFound), {
      code: 'VALIDATION_ERROR',
      details: { fieldErrors: { email: notFound } },
    }));
    renderRegisterForm();
    fillForm({ ...validValues, email: 'ana@noexiste.com' });

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    expect(await screen.findByRole('alert')).toHaveTextContent(notFound);
  });

  test('keeps errors without a field in the general box', async () => {
    registerUser.mockRejectedValue(Object.assign(new Error('No fue posible conectar con el servidor.'), { code: null }));
    renderRegisterForm();
    fillForm(validValues);

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.submit }));

    expect(
      await screen.findByText('No fue posible conectar con el servidor.', { selector: '.status-message' })
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
