import axios from 'axios';
import { apiBaseUrl } from '../config/env';

const authApi = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json'
  }
});

const getErrorMessage = (error) =>
  error.response?.data?.message || 'No fue posible conectar con el servidor.';

export const registerUser = async (payload) => {
  try {
    const response = await authApi.post('/auth/register', payload);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};

export const verifyEmailCode = async (payload) => {
  try {
    const response = await authApi.post('/auth/verify-email', payload);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};
