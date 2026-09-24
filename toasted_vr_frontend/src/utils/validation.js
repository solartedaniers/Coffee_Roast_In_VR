import esTexts from '../locals/es.json';

// Misma regla que EmailPolicy en el backend: dominio con al menos un punto y
// extensión de 2 o más letras (usuario@dominio.com).
const EMAIL_PATTERN =
  /^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$/;

export const isValidEmail = (email) => EMAIL_PATTERN.test((email || '').trim());

export const validateEmail = (email) => (isValidEmail(email) ? '' : esTexts.auth.validation.invalidEmail);

export const validateRegistrationForm = (formData, texts) => {
  const emailMessage = validateEmail(formData.email);
  if (emailMessage) {
    return emailMessage;
  }

  if (formData.password !== formData.confirmPassword) {
    return texts.messages.passwordMismatch;
  }

  return '';
};
