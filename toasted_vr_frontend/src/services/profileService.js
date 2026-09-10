import apiClient, { getErrorMessage } from './apiClient';

const PROFILE_PATH = '/users/me/profile';
const UNITY_ACCESS_CODE_PATH = '/users/me/unity-access-code';

async function handleRequest(request) {
  try {
    const response = await request();
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}

export async function updateProfile(payload) {
  return handleRequest(() => apiClient.patch(PROFILE_PATH, payload));
}

export async function fetchUnityAccessCodeStatus() {
  return handleRequest(() => apiClient.get(UNITY_ACCESS_CODE_PATH));
}

export async function createUnityAccessCode(currentPassword) {
  return handleRequest(() => apiClient.post(UNITY_ACCESS_CODE_PATH, { currentPassword }));
}

export async function revealUnityAccessCode(currentPassword) {
  return handleRequest(() => apiClient.post(`${UNITY_ACCESS_CODE_PATH}/reveal`, { currentPassword }));
}

export async function emailUnityAccessCode(currentPassword) {
  return handleRequest(() => apiClient.post(`${UNITY_ACCESS_CODE_PATH}/email`, { currentPassword }));
}

export async function regenerateUnityAccessCode(currentPassword) {
  return handleRequest(() => apiClient.post(`${UNITY_ACCESS_CODE_PATH}/regenerate`, { currentPassword }));
}
