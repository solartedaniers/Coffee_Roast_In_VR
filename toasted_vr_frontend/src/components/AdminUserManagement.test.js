import { fireEvent, render, screen, within } from '@testing-library/react';
import AdminUserManagement from './AdminUserManagement';
import esTexts from '../locals/es.json';
import { fetchAdminStats, fetchAllSessions, fetchUsers } from '../services/adminUserService';

jest.mock('../services/adminUserService', () => ({
  fetchUsers: jest.fn(),
  fetchUserDetail: jest.fn(),
  updateUserRole: jest.fn(),
  updateUserStatus: jest.fn(),
  fetchAllSessions: jest.fn(),
  fetchAdminStats: jest.fn(),
}));

const texts = esTexts.admin;
const currentUser = { id: 1, name: 'Admin', username: 'adminUser', role: 'ADMIN' };

const baseStats = {
  totalUsers: 5,
  activeUsers: 4,
  blockedUsers: 1,
  adminUsers: 1,
  knowledgeLevelCounts: { BEGINNER: 2, INTERMEDIATE: 1, ADVANCED: 1, NOT_SET: 1 },
  totalSessions: 5,
  sessionResultCounts: { PERFECT: 3, RAW: 1, BURNED: 1, BAKED: 0 },
  averageScore: 55.8,
  averageScoreByLevel: { BEGINNER: 75.5, INTERMEDIATE: null, ADVANCED: 5, NOT_SET: 61.5 },
};

const session = (overrides) => ({
  id: 1,
  userName: 'Ana Torres',
  userEmail: 'ana@gmail.com',
  userUsername: 'anaTorres',
  targetTemperature: 210,
  totalDurationSeconds: 780,
  finalTemperature: 205.4,
  result: 'PERFECT',
  qualityScore: 73,
  firstCrackReached: true,
  knowledgeLevel: 'INTERMEDIATE',
  createdAt: '2026-09-25T02:30:00Z',
  ...overrides,
});

const renderAdmin = () =>
  render(
    <AdminUserManagement
      texts={texts}
      profileTexts={esTexts.profile}
      currentUser={currentUser}
      onLogout={jest.fn()}
      onUserUpdate={jest.fn()}
    />
  );

const openSection = (name) => fireEvent.click(screen.getByRole('button', { name: new RegExp(name) }));

describe('AdminUserManagement (RF020)', () => {
  beforeEach(() => {
    fetchUsers.mockResolvedValue({ content: [], number: 0, size: 10, totalPages: 0, totalElements: 0 });
    fetchAdminStats.mockResolvedValue(baseStats);
    fetchAllSessions.mockResolvedValue({
      content: [session({}), session({ id: 2, knowledgeLevel: null, targetTemperature: 180, result: 'RAW', qualityScore: 33 })],
      number: 0,
      size: 20,
      totalPages: 1,
      totalElements: 2,
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('adds the average score by level, with "Sin nivel" and the general average', async () => {
    renderAdmin();
    openSection(texts.nav.stats);

    expect(await screen.findByText(texts.statsSection.averageTitle)).toBeInTheDocument();
    expect(screen.getByText(texts.sessionLevels.NOT_SET)).toBeInTheDocument();
    expect(screen.getByText(texts.statsSection.averageGeneral)).toBeInTheDocument();
    expect(screen.getByText('75.5%')).toBeInTheDocument();
    expect(screen.getByText('5%')).toBeInTheDocument();
    expect(screen.getByText('61.5%')).toBeInTheDocument();
    expect(screen.getByText('55.8%')).toBeInTheDocument();
    // Intermedio no tiene sesiones: muestra "—".
    expect(screen.getAllByText(texts.noValue)).toHaveLength(1);
  });

  test('keeps the metrics and distributions that were already shown', async () => {
    renderAdmin();
    openSection(texts.nav.stats);
    await screen.findByText(texts.statsSection.averageTitle);

    expect(screen.getByText(texts.statsSection.knowledgeTitle)).toBeInTheDocument();
    expect(screen.getByText(texts.statsSection.sessionResultsTitle)).toBeInTheDocument();
    expect(screen.getByText(texts.statsSection.notSet)).toBeInTheDocument();
    expect(screen.getByText(texts.metrics.total)).toBeInTheDocument();
    // Los nombres de nivel salen de es.json y se ven igual que antes en los
    // dos paneles (conocimiento y promedio por nivel).
    ['Principiante', 'Intermedio', 'Avanzado'].forEach((label) => {
      expect(screen.getAllByText(label)).toHaveLength(2);
    });
  });

  test('shows a dash when there are no sessions to average', async () => {
    fetchAdminStats.mockResolvedValue({
      ...baseStats,
      totalSessions: 0,
      averageScore: null,
      averageScoreByLevel: { BEGINNER: null, INTERMEDIATE: null, ADVANCED: null, NOT_SET: null },
    });
    renderAdmin();
    openSection(texts.nav.stats);

    await screen.findByText(texts.statsSection.averageTitle);
    expect(screen.getAllByText(texts.noValue)).toHaveLength(5);
  });

  test('adds the level and target temperature columns to the session list', async () => {
    renderAdmin();
    openSection(texts.nav.simulations);

    await screen.findByText('210.0°C');
    const headers = screen.getAllByRole('columnheader').map((header) => header.textContent);
    expect(headers).toEqual([
      texts.simulationsSection.columns.user,
      texts.simulationsSection.columns.level,
      texts.simulationsSection.columns.result,
      texts.simulationsSection.columns.score,
      texts.simulationsSection.columns.duration,
      texts.simulationsSection.columns.targetTemp,
      texts.simulationsSection.columns.temp,
      texts.simulationsSection.columns.date,
    ]);

    const [, withLevel, withoutLevel] = screen.getAllByRole('row');
    expect(within(withLevel).getByText('Intermedio')).toBeInTheDocument();
    expect(within(withLevel).getByText('210.0°C')).toBeInTheDocument();
    expect(within(withLevel).getByText('205.4°C')).toBeInTheDocument();
    expect(within(withoutLevel).getByText(texts.sessionLevels.NOT_SET)).toBeInTheDocument();
    expect(within(withoutLevel).getByText('180.0°C')).toBeInTheDocument();
  });
});

describe('AdminUserManagement – tabla de usuarios', () => {
  const longEmail = 'maria.fernanda.rodriguez.bastidas.pruebas@universidaddenarino.edu.co';

  beforeEach(() => {
    fetchUsers.mockResolvedValue({
      content: [{
        id: 5,
        name: 'María Fernanda Rodríguez Bastidas',
        email: longEmail,
        username: 'mafe_rodriguez_2026',
        role: 'PLAYER',
        enabled: true,
        emailVerified: true,
        createdAt: '2026-09-20T15:00:00Z',
      }],
      number: 0,
      size: 10,
      totalPages: 1,
      totalElements: 1,
    });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // Los textos largos se recortan con "…" por CSS; el valor completo queda
  // disponible al pasar el mouse.
  test('keeps the full name, email and username in the title of the truncated cells', async () => {
    renderAdmin();

    const emailCell = await screen.findByText(longEmail);
    expect(emailCell).toHaveAttribute('title', longEmail);
    expect(emailCell).toHaveClass('admin-truncate-cell');
    expect(screen.getByTitle('María Fernanda Rodríguez Bastidas')).toHaveClass('admin-truncate-cell');
    expect(screen.getByTitle('mafe_rodriguez_2026')).toHaveClass('admin-truncate-cell');
    expect(screen.getByRole('table')).toHaveClass('admin-users-table');
  });
});

describe('AdminUserManagement – cerrar sesión', () => {
  beforeEach(() => {
    fetchUsers.mockResolvedValue({ content: [], number: 0, size: 10, totalPages: 0, totalElements: 0 });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows the logout button at the top of the content, with the player style, and not in the sidebar', async () => {
    const onLogout = jest.fn();
    render(
      <AdminUserManagement
        texts={texts}
        profileTexts={esTexts.profile}
        currentUser={currentUser}
        onLogout={onLogout}
        onUserUpdate={jest.fn()}
      />
    );

    const logoutButton = await screen.findByRole('button', { name: texts.buttons.logout });
    expect(logoutButton).toHaveClass('secondary-button', 'admin-logout-btn');
    expect(screen.getAllByRole('button', { name: texts.buttons.logout })).toHaveLength(1);
    expect(within(screen.getByRole('main')).getByRole('button', { name: texts.buttons.logout })).toBe(logoutButton);
    expect(within(screen.getByRole('navigation')).queryByRole('button', { name: texts.buttons.logout })).toBeNull();

    fireEvent.click(logoutButton);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });
});
