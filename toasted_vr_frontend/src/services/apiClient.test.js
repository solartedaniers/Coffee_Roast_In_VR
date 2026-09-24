import { toApiError } from './apiClient';
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
