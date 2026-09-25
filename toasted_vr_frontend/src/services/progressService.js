import apiClient, { getErrorMessage } from './apiClient';

// Progreso del jugador (RF016): historial, resumen y detalle de sus sesiones.
const ROASTING_SESSIONS_PATH = '/roasting/sessions';

/** Página del historial (la más reciente primero); result filtra por resultado si viene. */
export async function getSessionHistory({ page = 0, result = '' } = {}) {
  try {
    const params = result ? { page, result } : { page };
    const response = await apiClient.get(ROASTING_SESSIONS_PATH, { params });
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}

export async function getSessionSummary() {
  try {
    const response = await apiClient.get(`${ROASTING_SESSIONS_PATH}/summary`);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}

export async function getSessionDetail(sessionId) {
  try {
    const response = await apiClient.get(`${ROASTING_SESSIONS_PATH}/${sessionId}`);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}
