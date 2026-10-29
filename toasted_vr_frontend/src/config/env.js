const defaultApiBaseUrl = 'http://localhost:8081/api/v1';

export const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || defaultApiBaseUrl;

// Cada cuánto se vuelve a pedir una sesión cuya calificación de la IA está pendiente, y cuántas
// veces antes de avisar que está tardando.
export const evaluationPollIntervalMs = Number(process.env.REACT_APP_EVALUATION_POLL_INTERVAL_MS) || 5000;
export const evaluationPollMaxAttempts = Number(process.env.REACT_APP_EVALUATION_POLL_MAX_ATTEMPTS) || 24;
