import { isValidEmail, validateRegistrationForm } from './validation';
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

    expect(validateRegistrationForm(formData, texts)).toBe(esTexts.auth.validation.invalidEmail);
  });

  test('accepts a valid email with matching passwords', () => {
    const formData = { email: 'a@gmail.com', password: 'Password1', confirmPassword: 'Password1' };

    expect(validateRegistrationForm(formData, texts)).toBe('');
  });
});
