import apiClient from './apiClient';
import { ROAST_SESSION_SAVED_EVENT, saveRoastingSession } from './simulationService';

jest.mock('./apiClient', () => ({
  __esModule: true,
  default: { post: jest.fn(), get: jest.fn() },
  getErrorMessage: (error) => error.message,
}));

// El aviso del ranking escucha este evento para volver a consultar al guardar un tueste.
describe('saveRoastingSession', () => {
  const listener = jest.fn();

  beforeEach(() => {
    window.addEventListener(ROAST_SESSION_SAVED_EVENT, listener);
  });

  afterEach(() => {
    window.removeEventListener(ROAST_SESSION_SAVED_EVENT, listener);
    jest.clearAllMocks();
  });

  test('announces that a roast was saved', async () => {
    apiClient.post.mockResolvedValue({ data: { id: 5 } });

    await expect(saveRoastingSession({ qualityScore: 70 })).resolves.toEqual({ id: 5 });
    expect(listener).toHaveBeenCalledTimes(1);
  });

  test('announces nothing when the server rejects the roast', async () => {
    apiClient.post.mockRejectedValue(new Error('No se guardó la sesión'));

    await expect(saveRoastingSession({ qualityScore: 70 })).rejects.toThrow('No se guardó la sesión');
    expect(listener).not.toHaveBeenCalled();
  });
});
