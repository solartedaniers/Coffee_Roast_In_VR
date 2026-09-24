import axios from 'axios';
import apiClient, { ACCOUNT_BLOCKED_EVENT, toApiError } from './apiClient';
import { readSession, saveSession } from './sessionService';
import esTexts from '../locals/es.json';

describe('toApiError', () => {
  test('keeps the backend message and exposes code, details and status', () => {
    const axiosError = {
      response: {
        status: 400,
        data: { message: 'El código no es correcto.', code: 'INVALID_CODE', details: { remainingAttempts: 3 } }
      }
    };

    const apiError = toApiError(axiosError);

    expect(apiError).toBeInstanceOf(Error);
    expect(apiError.message).toBe('El código no es correcto.');
    expect(apiError.code).toBe('INVALID_CODE');
    expect(apiError.details).toEqual({ remainingAttempts: 3 });
    expect(apiError.status).toBe(400);
  });

  test('falls back to the connection message when there is no response', () => {
    const apiError = toApiError(new Error('Network Error'));

    expect(apiError.message).toBe(esTexts.app.errors.connection);
    expect(apiError.code).toBeNull();
    expect(apiError.details).toBeNull();
  });
});

describe('apiClient response interceptor', () => {
  const originalAdapter = apiClient.defaults.adapter;

  const rejectWith = (status, data) => (config) =>
    Promise.reject(Object.assign(new Error('Request failed'), { config, response: { status, data } }));

  let blockedListener;

  beforeEach(() => {
    saveSession({ accessToken: 'access', refreshToken: 'refresh', user: { id: 1 } });
    blockedListener = jest.fn();
    window.addEventListener(ACCOUNT_BLOCKED_EVENT, blockedListener);
  });

  afterEach(() => {
    window.removeEventListener(ACCOUNT_BLOCKED_EVENT, blockedListener);
    apiClient.defaults.adapter = originalAdapter;
    jest.restoreAllMocks();
    window.localStorage.clear();
  });

  test('closes the local session without refreshing when the account is blocked', async () => {
    const refreshSpy = jest.spyOn(axios, 'post');
    apiClient.defaults.adapter = rejectWith(401, { code: 'ACCOUNT_BLOCKED' });

    await expect(apiClient.get('/users/me/profile')).rejects.toBeDefined();

    expect(refreshSpy).not.toHaveBeenCalled();
    expect(readSession()).toBeNull();
    expect(blockedListener).toHaveBeenCalledTimes(1);
  });

  test('announces the block when the refresh itself is rejected for a blocked account', async () => {
    jest.spyOn(axios, 'post').mockRejectedValue({ response: { status: 403, data: { code: 'ACCOUNT_BLOCKED' } } });
    apiClient.defaults.adapter = rejectWith(401, { code: 'UNAUTHORIZED' });

    await expect(apiClient.get('/users/me/profile')).rejects.toBeDefined();

    expect(readSession()).toBeNull();
    expect(blockedListener).toHaveBeenCalledTimes(1);
  });
});
