import { fireEvent, render, screen } from '@testing-library/react';
import ProfileSettings from './ProfileSettings';
import esTexts from '../locals/es.json';

jest.mock('../services/profileService', () => ({
  updateProfile: jest.fn(),
}));

const currentUser = {
  name: 'Ana Torres',
  email: 'ana@toastedvr.test',
  username: 'ana.torres',
  role: 'PLAYER',
  knowledgeLevel: 'BEGINNER',
  profileImageUrl: '',
};

const renderProfileSettings = () => render(
  <ProfileSettings
    texts={esTexts.profile}
    knowledgeTexts={esTexts.knowledgeLevel}
    currentUser={currentUser}
    isOpen
    onClose={jest.fn()}
    onUserUpdate={jest.fn()}
  />
);

test('shows read-only profile information before edit mode', () => {
  renderProfileSettings();

  expect(screen.getByRole('button', { name: 'Editar perfil' })).toBeInTheDocument();
  expect(screen.getByDisplayValue(currentUser.username)).toHaveAttribute('readonly');
  expect(screen.queryByLabelText('contraseña actual')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
});

test('restores original values when edit mode is cancelled', () => {
  renderProfileSettings();

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
