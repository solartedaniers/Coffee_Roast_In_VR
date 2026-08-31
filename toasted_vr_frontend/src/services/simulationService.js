import apiClient, { getErrorMessage } from './apiClient';

const ROASTING_SESSIONS_PATH = '/roasting/sessions';

export async function saveRoastingSession(sessionData) {
  try {
    const response = await apiClient.post(ROASTING_SESSIONS_PATH, sessionData);
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
