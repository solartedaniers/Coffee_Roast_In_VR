import axios from 'axios';
import { apiBaseUrl } from '../config/env';
import { readSession } from './sessionService';

export const getErrorMessage = (error) =>
  error.response?.data?.message || 'No fue posible conectar con el servidor.';

const apiClient = axios.create({
  baseURL: apiBaseUrl,
  headers: {
    'Content-Type': 'application/json'
  }
});

apiClient.interceptors.request.use((config) => {
  const session = readSession();
  if (session?.accessToken) {
    config.headers.Authorization = `Bearer ${session.accessToken}`;
  }

  return config;
});

export default apiClient;
