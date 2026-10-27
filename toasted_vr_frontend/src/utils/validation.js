import esTexts from '../locals/es.json';

// Misma regla que EmailPolicy en el backend: dominio con al menos un punto y
// extensión de 2 o más letras (usuario@dominio.com).
const EMAIL_PATTERN =
  /^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$/;

// Mismo @Pattern que RegisterUserRequest.name: letras de cualquier idioma
// (con tildes y ñ) y espacios. El vacío lo reporta el campo obligatorio.
const NAME_PATTERN = /^[\p{L}\p{M} ]*$/u;

// Deben coincidir con app.email-domain en application.yml (lo revisa
// emailDomainContract.test.js). Solo sirven para detectar errores de
// escritura; cualquier otro dominio real se acepta.
export const KNOWN_EMAIL_PROVIDERS = [
  'gmail.com', 'hotmail.com', 'outlook.com', 'yahoo.com', 'icloud.com', 'hotmail.es', 'outlook.es', 'yahoo.es',
];
export const MAX_EMAIL_TYPO_DISTANCE = 1;

export const isValidEmail = (email) => EMAIL_PATTERN.test((email || '').trim());

export const validateEmail = (email) => (isValidEmail(email) ? '' : esTexts.auth.validation.invalidEmail);

export const validateName = (name, texts) => (NAME_PATTERN.test(name || '') ? '' : texts.messages.nameLettersOnly);

// Distancia de Damerau-Levenshtein (OSA), igual que EmailDomainPolicy.editDistance.
export const editDistance = (first, second) => {
  const distance = Array.from({ length: first.length + 1 }, (_, i) =>
    Array.from({ length: second.length + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0))
  );
  for (let i = 1; i <= first.length; i++) {
    for (let j = 1; j <= second.length; j++) {
      const cost = first[i - 1] === second[j - 1] ? 0 : 1;
      distance[i][j] = Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1, distance[i - 1][j - 1] + cost);
      if (i > 1 && j > 1 && first[i - 1] === second[j - 2] && first[i - 2] === second[j - 1]) {
        distance[i][j] = Math.min(distance[i][j], distance[i - 2][j - 2] + 1);
      }
    }
  }
  return distance[first.length][second.length];
};

/** Proveedor conocido que el usuario quiso escribir (gail.com -> gmail.com), o null. */
export const findEmailDomainSuggestion = (email) => {
  const domain = (email || '').trim().toLowerCase().split('@').pop();
  if (KNOWN_EMAIL_PROVIDERS.includes(domain)) {
    return null;
  }
  return KNOWN_EMAIL_PROVIDERS.find((provider) => editDistance(domain, provider) <= MAX_EMAIL_TYPO_DISTANCE) ?? null;
};

/** Error del correo en el registro: formato y dominio mal escrito. Que el dominio exista lo revisa el servidor. */
export const validateRegistrationEmail = (email) => {
  const formatMessage = validateEmail(email);
  if (formatMessage) {
    return formatMessage;
  }
  const suggestion = findEmailDomainSuggestion(email);
  if (!suggestion) {
    return '';
  }
  const trimmed = email.trim();
  return esTexts.auth.validation.emailDomainTypo
    .replace('{domain}', trimmed.split('@').pop().toLowerCase())
    .replace('{suggestion}', `${trimmed.slice(0, trimmed.lastIndexOf('@') + 1)}${suggestion}`);
};

/** Devuelve los errores locales del registro como { campo: mensaje }. */
export const validateRegistrationForm = (formData, texts) => {
  const errors = {};

  const nameMessage = validateName(formData.name, texts);
  if (nameMessage) {
    errors.name = nameMessage;
  }

  const emailMessage = validateRegistrationEmail(formData.email);
  if (emailMessage) {
    errors.email = emailMessage;
  }

  if (formData.password !== formData.confirmPassword) {
    errors.confirmPassword = texts.messages.passwordMismatch;
  }

  return errors;
};
