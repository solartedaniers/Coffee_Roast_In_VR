import axios from 'axios';
import { apiBaseUrl } from '../config/env';
import { readSession, saveSession, clearSession } from './sessionService';

export const getErrorMessage = (error) =>
  error.response?.data?.message || 'No fue posible conectar con el servidor.';

const AUTH_PATH_PREFIX = '/auth/';
const SESSION_EXPIRED_EVENT = 'toastedvr:session-expired';

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

// Comparten esta promesa las solicitudes que expiran al mismo tiempo,
// para no rotar el refresh token varias veces en paralelo.
let refreshPromise = null;

const refreshSession = async () => {
  const session = readSession();
  if (!session?.refreshToken) {
    throw new Error('No hay refresh token disponible.');
  }

  const response = await axios.post(`${apiBaseUrl}/auth/refresh`, {
    refreshToken: session.refreshToken
  });

  const nextSession = {
    ...session,
    accessToken: response.data.accessToken,
    refreshToken: response.data.refreshToken,
    expiresAt: response.data.expiresAt
  };
  saveSession(nextSession);
  return nextSession;
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error;
    const isAuthEndpoint = config?.url?.startsWith(AUTH_PATH_PREFIX);

    if (!config || response?.status !== 401 || isAuthEndpoint || config._retry) {
      return Promise.reject(error);
    }

    config._retry = true;

    try {
      refreshPromise = refreshPromise || refreshSession();
      const nextSession = await refreshPromise;
      refreshPromise = null;

      config.headers.Authorization = `Bearer ${nextSession.accessToken}`;
      return apiClient(config);
    } catch (refreshError) {
      refreshPromise = null;
      clearSession();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
      return Promise.reject(error);
    }
  }
);

export default apiClient;
