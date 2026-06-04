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
