import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import ProfileSettings from './ProfileSettings';
import esTexts from '../locals/es.json';
import {
  createUnityAccessCode,
  emailUnityAccessCode,
  fetchUnityAccessCodeStatus,
  regenerateUnityAccessCode,
  revealUnityAccessCode,
  updateProfile,
} from '../services/profileService';

jest.mock('../services/profileService', () => ({
  updateProfile: jest.fn(),
  fetchUnityAccessCodeStatus: jest.fn(),
  createUnityAccessCode: jest.fn(),
  revealUnityAccessCode: jest.fn(),
  emailUnityAccessCode: jest.fn(),
  regenerateUnityAccessCode: jest.fn(),
}));

const currentUser = {
  name: 'Ana Torres',
  email: 'ana@toastedvr.test',
  username: 'ana.torres',
  role: 'PLAYER',
  knowledgeLevel: 'BEGINNER',
  profileImageUrl: '',
};

const renderProfileSettings = (overrides = {}) => render(
  <ProfileSettings
    texts={esTexts.profile}
    knowledgeTexts={esTexts.knowledgeLevel}
    currentUser={currentUser}
    isOpen
    onClose={jest.fn()}
    onUserUpdate={jest.fn()}
    {...overrides}
  />
);

beforeEach(() => {
  jest.clearAllMocks();
  fetchUnityAccessCodeStatus.mockResolvedValue({ exists: false });
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: { writeText: jest.fn().mockResolvedValue(undefined) },
  });
});

test('shows read-only profile information before edit mode', async () => {
  renderProfileSettings();

  await screen.findByRole('button', { name: 'Crear código' });
  expect(screen.getByRole('button', { name: 'Editar perfil' })).toBeInTheDocument();
  expect(screen.getByDisplayValue(currentUser.username)).toHaveAttribute('readonly');
  expect(screen.queryByLabelText('contraseña actual')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
});

test('restores original values when edit mode is cancelled', async () => {
  renderProfileSettings();

  await screen.findByRole('button', { name: 'Crear código' });
  fireEvent.click(screen.getByRole('button', { name: 'Editar perfil' }));
  const usernameInput = screen.getByDisplayValue(currentUser.username);
  fireEvent.change(usernameInput, { target: { value: 'ana.actualizada' } });

  expect(screen.getByRole('button', { name: '¿Quieres cambiar tu contraseña?' })).toBeInTheDocument();
  expect(screen.queryByLabelText('contraseña actual')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: '¿Quieres cambiar tu contraseña?' }));
  const currentPasswordInput = screen.getByLabelText('contraseña actual');
  fireEvent.change(currentPasswordInput, { target: { value: 'Password123!' } });

  expect(currentPasswordInput).toBeRequired();
  expect(screen.getByLabelText('Nueva contraseña')).toBeRequired();
  expect(screen.getByLabelText('Confirmar nueva contraseña')).toBeRequired();

  fireEvent.click(screen.getByRole('button', { name: 'No, cancelar cambio de contraseña' }));

  expect(screen.queryByLabelText('contraseña actual')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '¿Quieres cambiar tu contraseña?' }));
  expect(screen.getByLabelText('contraseña actual')).toHaveValue('');

  fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

  expect(screen.getByDisplayValue(currentUser.username)).toHaveAttribute('readonly');
  expect(screen.getByDisplayValue(currentUser.username)).toHaveValue(currentUser.username);
  expect(screen.queryByLabelText('contraseña actual')).not.toBeInTheDocument();
});

test('loads the Unity access status without requesting or displaying a raw code', async () => {
  fetchUnityAccessCodeStatus.mockResolvedValue({ exists: true, createdAt: '2026-09-03T10:00:00' });

  renderProfileSettings();

  expect(await screen.findByText('Configurado')).toBeInTheDocument();
  expect(fetchUnityAccessCodeStatus).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('K7MP-4XQ2')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Ver código' })).toBeInTheDocument();
});

test('creates and copies a Unity access code after password confirmation', async () => {
  createUnityAccessCode.mockResolvedValue({ code: 'K7MP-4XQ2' });

  renderProfileSettings();

  const createButton = await screen.findByRole('button', { name: 'Crear código' });
  fireEvent.click(createButton);

  const confirmation = screen.getByRole('dialog', { name: 'Crear código de acceso' });
  fireEvent.change(within(confirmation).getByLabelText('Contraseña actual'), {
    target: { value: 'Password123!' },
  });
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Crear código' }));

  expect(await screen.findByText('K7MP-4XQ2')).toBeInTheDocument();
  expect(createUnityAccessCode).toHaveBeenCalledWith('Password123!');

  fireEvent.click(within(confirmation).getByRole('button', { name: 'Copiar código' }));
  await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('K7MP-4XQ2'));
  expect(await within(confirmation).findByText('Código copiado correctamente.')).toBeInTheDocument();
});

test('reveals a configured code and clears sensitive state when the confirmation closes', async () => {
  fetchUnityAccessCodeStatus.mockResolvedValue({ exists: true });
  revealUnityAccessCode.mockResolvedValue({ code: 'ABCD-2345' });

  renderProfileSettings();

  fireEvent.click(await screen.findByRole('button', { name: 'Ver código' }));
  let confirmation = screen.getByRole('dialog', { name: 'Ver código de acceso' });
  const passwordInput = within(confirmation).getByLabelText('Contraseña actual');
  fireEvent.change(passwordInput, { target: { value: 'Password123!' } });
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Ver código' }));

  expect(await screen.findByText('ABCD-2345')).toBeInTheDocument();
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Cerrar' }));
  expect(screen.queryByText('ABCD-2345')).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: 'Ver código' }));
  confirmation = screen.getByRole('dialog', { name: 'Ver código de acceso' });
  expect(within(confirmation).getByLabelText('Contraseña actual')).toHaveValue('');
});

test('emails and regenerates a configured Unity access code', async () => {
  fetchUnityAccessCodeStatus.mockResolvedValue({ exists: true });
  emailUnityAccessCode.mockResolvedValue(undefined);
  regenerateUnityAccessCode.mockResolvedValue({ code: 'WXYZ-6789' });

  renderProfileSettings();

  fireEvent.click(await screen.findByRole('button', { name: 'Enviar por correo' }));
  let confirmation = screen.getByRole('dialog', { name: 'Enviar código por correo' });
  fireEvent.change(within(confirmation).getByLabelText('Contraseña actual'), {
    target: { value: 'Password123!' },
  });
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Enviar código' }));

  expect(await screen.findByText('El código se envió correctamente a tu correo registrado.')).toBeInTheDocument();
  expect(emailUnityAccessCode).toHaveBeenCalledWith('Password123!');

  fireEvent.click(screen.getByRole('button', { name: 'Regenerar código' }));
  confirmation = screen.getByRole('dialog', { name: 'Regenerar código de acceso' });
  expect(within(confirmation).getByText(/El código anterior dejará de funcionar/)).toBeInTheDocument();
  fireEvent.change(within(confirmation).getByLabelText('Contraseña actual'), {
    target: { value: 'NewPassword123!' },
  });
  fireEvent.click(within(confirmation).getByRole('button', { name: 'Regenerar código' }));

  expect(await screen.findByText('WXYZ-6789')).toBeInTheDocument();
  expect(regenerateUnityAccessCode).toHaveBeenCalledWith('NewPassword123!');
});

test('shows local password errors under their fields without saving', async () => {
  renderProfileSettings();

  await screen.findByRole('button', { name: 'Crear código' });
  fireEvent.click(screen.getByRole('button', { name: 'Editar perfil' }));
  fireEvent.click(screen.getByRole('button', { name: '¿Quieres cambiar tu contraseña?' }));
  fireEvent.change(screen.getByLabelText('Nueva contraseña'), { target: { value: 'Password123' } });
  fireEvent.change(screen.getByLabelText('Confirmar nueva contraseña'), { target: { value: 'Different123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

  const alerts = screen.getAllByRole('alert').map((alert) => alert.textContent);
  expect(alerts).toEqual([
    esTexts.profile.messages.currentPasswordRequired,
    esTexts.profile.messages.passwordMismatch,
  ]);
  expect(updateProfile).not.toHaveBeenCalled();
});

test('shows a taken username under the username field and clears it on change', async () => {
  updateProfile.mockRejectedValue(Object.assign(new Error('El nombre de usuario ya está en uso.'), {
    code: 'CONFLICT',
    details: { fieldErrors: { username: 'El nombre de usuario ya está en uso.' } },
  }));
  renderProfileSettings();

  await screen.findByRole('button', { name: 'Crear código' });
  fireEvent.click(screen.getByRole('button', { name: 'Editar perfil' }));
  const usernameInput = screen.getByDisplayValue(currentUser.username);
  fireEvent.change(usernameInput, { target: { value: 'takenName' } });
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('El nombre de usuario ya está en uso.');
  expect(usernameInput).toHaveAttribute('aria-invalid', 'true');

  fireEvent.change(usernameInput, { target: { value: 'otherName' } });

  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
