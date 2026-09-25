import {
  editDistance,
  findEmailDomainSuggestion,
  isValidEmail,
  validateName,
  validateRegistrationEmail,
  validateRegistrationForm,
} from './validation';
import esTexts from '../locals/es.json';

describe('isValidEmail', () => {
  test.each(['a@gmail.com', 'user.name+tag@mail.udenar.edu.co', 'ANA@Example.IO'])('accepts %s', (email) => {
    expect(isValidEmail(email)).toBe(true);
  });

  test.each(['a@gmailcom', 'a@gmail.c', 'a@gmail.', 'a@.com', 'a@gmail..com', 'agmail.com', '', null])(
    'rejects %s',
    (email) => {
      expect(isValidEmail(email)).toBe(false);
    }
  );
});

describe('validateRegistrationForm', () => {
  const texts = esTexts.auth.register;

  test('rejects an email without a dot in the domain', () => {
    const formData = { email: 'a@gmailcom', password: 'Password1', confirmPassword: 'Password1' };

    expect(validateRegistrationForm(formData, texts)).toEqual({ email: esTexts.auth.validation.invalidEmail });
  });

  test('returns every local error by field', () => {
    const formData = { email: 'a@gmailcom', password: 'Password1', confirmPassword: 'Password2' };

    expect(validateRegistrationForm(formData, texts)).toEqual({
      email: esTexts.auth.validation.invalidEmail,
      confirmPassword: texts.messages.passwordMismatch,
    });
  });

  test('accepts a valid email with matching passwords', () => {
    const formData = { email: 'a@gmail.com', password: 'Password1', confirmPassword: 'Password1' };

    expect(validateRegistrationForm(formData, texts)).toEqual({});
  });

  test('rejects a name with numbers and a misspelled email domain', () => {
    const formData = { name: 'Ana 2', email: 'ana@gail.com', password: 'Password1', confirmPassword: 'Password1' };

    expect(validateRegistrationForm(formData, texts)).toEqual({
      name: texts.messages.nameLettersOnly,
      email: 'Correo inexistente: el dominio «gail.com» no existe. ¿Quisiste decir ana@gmail.com?',
    });
  });
});

describe('validateName', () => {
  const texts = esTexts.auth.register;

  test.each(['Ana', 'José Ñúñez Gómez', 'María Fernanda', 'Zoë', ''])('accepts "%s"', (name) => {
    expect(validateName(name, texts)).toBe('');
  });

  test.each(['Ana1', 'Juan_Pérez', 'Ana-María', 'María!', "O'Neil", 'ana@', 'Pedro 2'])('rejects "%s"', (name) => {
    expect(validateName(name, texts)).toBe(texts.messages.nameLettersOnly);
  });
});

describe('findEmailDomainSuggestion', () => {
  test.each([
    ['ana@gail.com', 'gmail.com'],
    ['ana@gmial.com', 'gmail.com'],
    ['ana@gmail.con', 'gmail.com'],
    ['ana@GMAIL.CO', 'gmail.com'],
    ['ana@hotmial.com', 'hotmail.com'],
    ['ana@outlok.com', 'outlook.com'],
    ['ana@yaho.com', 'yahoo.com'],
  ])('%s -> %s', (email, provider) => {
    expect(findEmailDomainSuggestion(email)).toBe(provider);
  });

  test.each(['ana@gmail.com', 'ana@hotmail.es', 'ana@udenar.edu.co', 'ana@protonmail.com', 'ana@live.com'])(
    'does not flag %s',
    (email) => {
      expect(findEmailDomainSuggestion(email)).toBeNull();
    }
  );

  test('counts a swapped pair of letters as one edit, like the backend', () => {
    expect(editDistance('gmial.com', 'gmail.com')).toBe(1);
    expect(editDistance('yahoo.es', 'yahoo.com')).toBe(3);
  });

  test('keeps the user part of the address in the suggestion', () => {
    expect(validateRegistrationEmail(' Juan.Perez@Gail.com ')).toBe(
      'Correo inexistente: el dominio «gail.com» no existe. ¿Quisiste decir Juan.Perez@gmail.com?'
    );
  });
});
