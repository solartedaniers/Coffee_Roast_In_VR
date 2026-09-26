import apiClient, { getErrorMessage } from './apiClient';

const ROASTING_SESSIONS_PATH = '/roasting/sessions';

// Se emite al guardar un tueste: el aviso de la corona vuelve a consultar el
// ranking sin que la simulación tenga que saber de él.
export const ROAST_SESSION_SAVED_EVENT = 'toastedvr:roast-session-saved';

export async function saveRoastingSession(sessionData) {
  try {
    const response = await apiClient.post(ROASTING_SESSIONS_PATH, sessionData);
    window.dispatchEvent(new Event(ROAST_SESSION_SAVED_EVENT));
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}

export async function getRoastingFeedback(sessionId) {
  try {
    const response = await apiClient.get(`${ROASTING_SESSIONS_PATH}/${sessionId}/feedback`);
    return response.data.feedback || '';
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}
