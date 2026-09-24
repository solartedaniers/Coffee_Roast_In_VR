import apiClient, { toApiError } from './apiClient';

export const registerUser = async (payload) => {
  try {
    const response = await apiClient.post('/auth/register', payload);
    return response.data;
  } catch (error) {
    throw toApiError(error);
  }
};

export const verifyEmailCode = async (payload) => {
  try {
    const response = await apiClient.post('/auth/verify-email', payload);
    return response.data;
  } catch (error) {
    throw toApiError(error);
  }
};

export const resendVerificationCode = async (payload) => {
  try {
    const response = await apiClient.post('/auth/resend-verification-code', payload);
    return response.data;
  } catch (error) {
    throw toApiError(error);
  }
};

export const loginUser = async (payload) => {
  try {
    const response = await apiClient.post('/auth/login', payload);
    return response.data;
  } catch (error) {
    throw toApiError(error);
  }
};

export const logoutUser = async () => {
  try {
    const response = await apiClient.post('/auth/logout');
    return response.data;
  } catch (error) {
    throw toApiError(error);
  }
};
