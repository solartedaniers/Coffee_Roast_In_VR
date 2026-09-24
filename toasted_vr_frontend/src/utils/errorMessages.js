// Devuelve el texto de es.json para el código de error del backend y, si no
// hay uno definido, el mensaje que envió la API.
export const resolveErrorMessage = (error, errorTexts) =>
  (error?.code && errorTexts?.[error.code]) || error?.message;
