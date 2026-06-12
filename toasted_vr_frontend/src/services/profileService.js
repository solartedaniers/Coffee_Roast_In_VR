import apiClient, { getErrorMessage } from './apiClient';

const PROFILE_PATH = '/users/me/profile';

export async function updateProfile(payload) {
  try {
    const response = await apiClient.patch(PROFILE_PATH, payload);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}
