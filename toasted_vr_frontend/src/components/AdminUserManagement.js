import React, { useEffect, useState, useCallback } from 'react';
import ProfileSettings from './ProfileSettings';
import {
  fetchUsers,
  fetchUserDetail,
  updateUserRole,
  updateUserStatus,
  fetchAllSessions,
  fetchAdminStats,
} from '../services/adminUserService';

// ── Constants ──────────────────────────────────────────────────────
const PAGE_SIZE_USERS = 10;
const PAGE_SIZE_SESSIONS = 20;

const SECTIONS = Object.freeze({
  USERS: 'users',
  STATS: 'stats',
  ROLES: 'roles',
  SIMULATIONS: 'simulations',
});

const NAV_ICONS = {
  [SECTIONS.USERS]: '👥',
  [SECTIONS.STATS]: '📊',
  [SECTIONS.ROLES]: '🔐',
  [SECTIONS.SIMULATIONS]: '☕',
};

const RESULT_COLORS = {
  PERFECT: 'result-pill-perfect',
  RAW: 'result-pill-raw',
  BURNED: 'result-pill-burned',
  BAKED: 'result-pill-baked',
};

const initialFilters = { name: '', email: '', enabled: '', role: '' };

// ── Utility ────────────────────────────────────────────────────────
function formatDuration(totalSeconds) {
  if (!totalSeconds) return '—';
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

// ── Component ──────────────────────────────────────────────────────
function AdminUserManagement({ texts, profileTexts, currentUser, onLogout, onUserUpdate }) {
  const [activeSection, setActiveSection] = useState(SECTIONS.USERS);
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  // --- Users/Roles state ---
  const [filters, setFilters] = useState(initialFilters);
  const [users, setUsers] = useState([]);
  const [usersPage, setUsersPage] = useState({
    number: 0,
    size: PAGE_SIZE_USERS,
    totalPages: 0,
    totalElements: 0,
  });
  const [isLoadingUsers, setIsLoadingUsers] = useState(true);

  // --- Detail panel ---
  const [panel, setPanel] = useState({ isOpen: false, user: null, pendingRole: '' });
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [isPendingAction, setIsPendingAction] = useState(false);

  // --- Stats ---
  const [stats, setStats] = useState(null);
  const [isLoadingStats, setIsLoadingStats] = useState(false);

  // --- Simulations ---
  const [sessions, setSessions] = useState([]);
  const [sessionsPage, setSessionsPage] = useState({
    number: 0,
    size: PAGE_SIZE_SESSIONS,
    totalPages: 0,
    totalElements: 0,
  });
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [sessionsLoaded, setSessionsLoaded] = useState(false);

  // --- Status message ---
  const [status, setStatus] = useState({ text: '', isError: false });

  // ── Data loaders ─────────────────────────────────────────────────

  const loadUsers = useCallback(async (pageNum = 0, activeFilters = filters) => {
    setIsLoadingUsers(true);
    try {
      const res = await fetchUsers({ ...activeFilters, page: pageNum, size: PAGE_SIZE_USERS });
      setUsers(res.content || []);
      setUsersPage((prev) => ({
        ...prev,
        number: res.number,
        totalPages: res.totalPages,
        totalElements: res.totalElements,
      }));
    } catch (err) {
      setStatus({ text: err.message, isError: true });
    } finally {
      setIsLoadingUsers(false);
    }
  }, [filters]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadStats = useCallback(async () => {
    if (stats) return;
    setIsLoadingStats(true);
    try {
      const data = await fetchAdminStats();
      setStats(data);
    } catch (err) {
      setStatus({ text: err.message, isError: true });
    } finally {
      setIsLoadingStats(false);
    }
  }, [stats]);

  const loadSessions = useCallback(async (pageNum = 0) => {
    setIsLoadingSessions(true);
    try {
      const res = await fetchAllSessions({ page: pageNum, size: PAGE_SIZE_SESSIONS });
      setSessions(res.content || []);
      setSessionsPage((prev) => ({
        ...prev,
        number: res.number,
        totalPages: res.totalPages,
        totalElements: res.totalElements,
      }));
      setSessionsLoaded(true);
    } catch (err) {
      setStatus({ text: err.message, isError: true });
    } finally {
      setIsLoadingSessions(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    loadUsers(0, initialFilters);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Load section data on first visit
  useEffect(() => {
    if (activeSection === SECTIONS.STATS) loadStats();
    if (activeSection === SECTIONS.SIMULATIONS && !sessionsLoaded) loadSessions(0);
  }, [activeSection]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Panel helpers ─────────────────────────────────────────────────

  const openPanel = useCallback(async (userSummary) => {
    setPanel({ isOpen: true, user: userSummary, pendingRole: userSummary.role });
    setIsLoadingDetail(true);
    try {
      const detail = await fetchUserDetail(userSummary.id);
      setPanel((prev) => ({ ...prev, user: detail, pendingRole: detail.role }));
    } catch {
      // keep summary data if detail fails
    } finally {
      setIsLoadingDetail(false);
    }
  }, []);

  const closePanel = useCallback(() => {
    setPanel({ isOpen: false, user: null, pendingRole: '' });
  }, []);

  // ── User actions ──────────────────────────────────────────────────

  const handleApplyFilters = async (e) => {
    e.preventDefault();
    await loadUsers(0, filters);
  };

  const handleResetFilters = async () => {
    const cleared = { ...initialFilters };
    setFilters(cleared);
    setStatus({ text: '', isError: false });
    await loadUsers(0, cleared);
  };

  const handleUsersPageChange = async (dir) => {
    const next = usersPage.number + dir;
    if (next < 0 || next >= usersPage.totalPages) return;
    await loadUsers(next);
  };

  const handleSessionsPageChange = async (dir) => {
    const next = sessionsPage.number + dir;
    if (next < 0 || next >= sessionsPage.totalPages) return;
    await loadSessions(next);
  };

  const handleStatusToggle = async () => {
    if (!panel.user || panel.user.id === currentUser.id) return;
    setIsPendingAction(true);
    try {
      const res = await updateUserStatus(panel.user.id, !panel.user.enabled);
      setStatus({ text: res.message, isError: false });
      setPanel((prev) => ({ ...prev, user: res }));
      await loadUsers(usersPage.number);
    } catch (err) {
      setStatus({ text: err.message, isError: true });
    } finally {
      setIsPendingAction(false);
    }
  };

  const handleRoleSubmit = async () => {
    if (!panel.user || panel.user.id === currentUser.id) return;
    if (panel.pendingRole === panel.user.role) return;
    setIsPendingAction(true);
    try {
      const res = await updateUserRole(panel.user.id, panel.pendingRole);
      setStatus({ text: res.message, isError: false });
      setPanel((prev) => ({ ...prev, user: res, pendingRole: res.role }));
      await loadUsers(usersPage.number);
    } catch (err) {
      setStatus({ text: err.message, isError: true });
    } finally {
      setIsPendingAction(false);
    }
  };

  const canEditPanelUser = panel.user && panel.user.id !== currentUser.id;

  // ── Render helpers ────────────────────────────────────────────────

  const renderUserTable = (showActions = true) => (
    <div className="users-table-wrapper">
      <table className="admin-compact-table">
        <thead>
          <tr>
            <th>{texts.table.name}</th>
            <th>{texts.table.email}</th>
            <th>{texts.table.role}</th>
            <th>{texts.table.status}</th>
            {showActions && <th>{texts.table.actions}</th>}
          </tr>
        </thead>
        <tbody>
          {isLoadingUsers && (
            <tr>
              <td colSpan={showActions ? 5 : 4} className="empty-state">
                {texts.loadingUsers}
              </td>
            </tr>
          )}
          {!isLoadingUsers && users.length === 0 && (
            <tr>
              <td colSpan={showActions ? 5 : 4} className="empty-state">
                {texts.empty}
              </td>
            </tr>
          )}
          {!isLoadingUsers && users.map((user) => (
            <tr
              key={user.id}
              className={panel.user?.id === user.id ? 'is-selected' : ''}
              onClick={() => openPanel(user)}
              style={{ cursor: 'pointer' }}
            >
              <td><strong>{user.name}</strong></td>
              <td className="admin-muted-cell">{user.email}</td>
              <td>{texts.roles[user.role.toLowerCase()] || user.role}</td>
              <td>
                <span className={`status-pill ${user.enabled ? 'active' : 'blocked'}`}>
                  {user.enabled ? texts.options.active : texts.options.blocked}
                </span>
              </td>
              {showActions && (
                <td>
                  <button
                    type="button"
                    className="admin-row-action-btn"
                    onClick={(e) => { e.stopPropagation(); openPanel(user); }}
                  >
                    {texts.detailPanel.title}
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );

  const renderUsersPagination = (page, onPageChange) => (
    <div className="pagination-row">
      <button
        type="button"
        className="secondary-button"
        onClick={() => onPageChange(-1)}
        disabled={page.number === 0}
      >
        {texts.buttons.previous}
      </button>
      <span>
        {texts.pageLabel
          .replace('{page}', page.totalPages === 0 ? 0 : page.number + 1)
          .replace('{totalPages}', page.totalPages)}
      </span>
      <button
        type="button"
        className="secondary-button"
        onClick={() => onPageChange(1)}
        disabled={page.number + 1 >= page.totalPages}
      >
        {texts.buttons.next}
      </button>
    </div>
  );

  // ── Section renderers ─────────────────────────────────────────────

  const renderUsersSection = () => (
    <section className="admin-section">
      <div className="admin-section-header">
        <div>
          <h2>{texts.listTitle}</h2>
          <p className="admin-section-subtitle">
            {texts.resultsLabel.replace('{count}', usersPage.totalElements)}
          </p>
        </div>
      </div>

      <form className="admin-compact-filters" onSubmit={handleApplyFilters}>
        <input
          className="field-input admin-filter-input"
          type="text"
          name="name"
          placeholder={texts.placeholders.name}
          value={filters.name}
          onChange={(e) => setFilters((p) => ({ ...p, name: e.target.value }))}
        />
        <input
          className="field-input admin-filter-input"
          type="email"
          name="email"
          placeholder={texts.placeholders.email}
          value={filters.email}
          onChange={(e) => setFilters((p) => ({ ...p, email: e.target.value }))}
        />
        <select
          className="field-input admin-filter-input"
          name="enabled"
          value={filters.enabled}
          onChange={(e) => setFilters((p) => ({ ...p, enabled: e.target.value }))}
        >
          <option value="">{texts.options.allStatuses}</option>
          <option value="true">{texts.options.active}</option>
          <option value="false">{texts.options.blocked}</option>
        </select>
        <select
          className="field-input admin-filter-input"
          name="role"
          value={filters.role}
          onChange={(e) => setFilters((p) => ({ ...p, role: e.target.value }))}
        >
          <option value="">{texts.options.allRoles}</option>
          <option value="ADMIN">{texts.roles.admin}</option>
          <option value="PLAYER">{texts.roles.player}</option>
        </select>
        <button className="primary-button admin-filter-btn" type="submit">
          {texts.buttons.search}
        </button>
        <button className="secondary-button admin-filter-btn" type="button" onClick={handleResetFilters}>
          {texts.buttons.clear}
        </button>
      </form>

      {renderUserTable(true)}
      {renderUsersPagination(usersPage, handleUsersPageChange)}
    </section>
  );

  const renderStatsSection = () => {
    if (isLoadingStats) {
      return <p className="empty-state">{texts.statsSection.loadingStats}</p>;
    }
    if (!stats) return null;

    const klOrder = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'NOT_SET'];
    const klLabels = {
      BEGINNER: texts.options?.allRoles ? 'Principiante' : 'Principiante',
      INTERMEDIATE: 'Intermedio',
      ADVANCED: 'Avanzado',
      NOT_SET: texts.statsSection.notSet,
    };

    const resultLabels = texts.simulationsSection?.results || {};
    const totalSessions = stats.totalSessions || 0;

    return (
      <section className="admin-section">
        <div className="admin-section-header">
          <div>
            <h2>{texts.statsSection.title}</h2>
            <p className="admin-section-subtitle">{texts.statsSection.subtitle}</p>
          </div>
        </div>

        <div className="admin-metrics-grid">
          {[
            { label: texts.metrics.total, value: stats.totalUsers },
            { label: texts.metrics.active, value: stats.activeUsers },
            { label: texts.metrics.blocked, value: stats.blockedUsers },
            { label: texts.metrics.admins, value: stats.adminUsers },
          ].map(({ label, value }) => (
            <article className="metric-card" key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </div>

        <div className="admin-stats-two-col">
          <div className="admin-stats-panel">
            <h3 className="admin-stats-subtitle">{texts.statsSection.knowledgeTitle}</h3>
            <div className="admin-dist-list">
              {klOrder.map((key) => {
                const count = stats.knowledgeLevelCounts?.[key] ?? 0;
                const total = stats.totalUsers || 1;
                const pct = Math.round((count / total) * 100);
                return (
                  <div className="admin-dist-row" key={key}>
                    <span className="admin-dist-label">{klLabels[key]}</span>
                    <div className="admin-dist-bar-track">
                      <div className="admin-dist-bar-fill" style={{ width: `${pct}%` }} />
                    </div>
                    <span className="admin-dist-count">{count}</span>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="admin-stats-panel">
            <h3 className="admin-stats-subtitle">{texts.statsSection.sessionResultsTitle}</h3>
            <div className="admin-dist-list">
              {['PERFECT', 'RAW', 'BURNED', 'BAKED'].map((key) => {
                const count = stats.sessionResultCounts?.[key] ?? 0;
                const pct = totalSessions > 0 ? Math.round((count / totalSessions) * 100) : 0;
                return (
                  <div className="admin-dist-row" key={key}>
                    <span className="admin-dist-label">{resultLabels[key] || key}</span>
                    <div className="admin-dist-bar-track">
                      <div className="admin-dist-bar-fill" style={{ width: `${pct}%` }} />
                    </div>
                    <span className="admin-dist-count">{count}</span>
                  </div>
                );
              })}
              <div className="admin-dist-row admin-dist-total">
                <span className="admin-dist-label">Total</span>
                <div className="admin-dist-bar-track"><div className="admin-dist-bar-fill" style={{ width: '100%', opacity: 0.3 }} /></div>
                <span className="admin-dist-count">{totalSessions}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    );
  };

  const renderRolesSection = () => (
    <section className="admin-section">
      <div className="admin-section-header">
        <div>
          <h2>{texts.rolesSection.title}</h2>
          <p className="admin-section-subtitle">{texts.rolesSection.subtitle}</p>
        </div>
      </div>
      {renderUserTable(true)}
      {renderUsersPagination(usersPage, handleUsersPageChange)}
    </section>
  );

  const renderSimulationsSection = () => (
    <section className="admin-section">
      <div className="admin-section-header">
        <div>
          <h2>{texts.simulationsSection.title}</h2>
          <p className="admin-section-subtitle">
            {texts.simulationsSection.subtitle}
            {sessionsPage.totalElements > 0 && ` · ${sessionsPage.totalElements} registros`}
          </p>
        </div>
      </div>

      {isLoadingSessions ? (
        <p className="empty-state">{texts.simulationsSection.loading}</p>
      ) : sessions.length === 0 ? (
        <p className="empty-state">{texts.simulationsSection.empty}</p>
      ) : (
        <>
          <div className="users-table-wrapper">
            <table className="admin-compact-table">
              <thead>
                <tr>
                  <th>{texts.simulationsSection.columns.user}</th>
                  <th>{texts.simulationsSection.columns.result}</th>
                  <th>{texts.simulationsSection.columns.score}</th>
                  <th>{texts.simulationsSection.columns.duration}</th>
                  <th>{texts.simulationsSection.columns.temp}</th>
                  <th>{texts.simulationsSection.columns.date}</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.id}>
                    <td>
                      <strong>{session.userName}</strong>
                      <br />
                      <small className="admin-muted-cell">{session.userUsername}</small>
                    </td>
                    <td>
                      <span className={`result-pill ${RESULT_COLORS[session.result] || ''}`}>
                        {texts.simulationsSection.results[session.result] || session.result}
                      </span>
                    </td>
                    <td className="admin-score-cell">{session.qualityScore}%</td>
                    <td>{formatDuration(session.totalDurationSeconds)}</td>
                    <td>{session.finalTemperature?.toFixed(1)}°C</td>
                    <td className="admin-muted-cell">
                      {new Date(session.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {renderUsersPagination(sessionsPage, handleSessionsPageChange)}
        </>
      )}
    </section>
  );

  const renderDetailPanel = () => {
    const user = panel.user;
    if (!user) return null;

    return (
      <>
        <div className="admin-detail-panel-header">
          <h3>{texts.detailPanel.title}</h3>
          <button type="button" className="secondary-button admin-close-btn" onClick={closePanel}>
            {texts.detailPanel.close}
          </button>
        </div>

        {isLoadingDetail ? (
          <p className="empty-state">{texts.loadingDetail}</p>
        ) : (
          <>
            <div className="admin-user-avatar-lg">
              {user.name.charAt(0).toUpperCase()}
            </div>

            <div className="detail-stack">
              {[
                [texts.detail.name, user.name],
                [texts.detail.email, user.email],
                [texts.detail.username, user.username],
                [texts.detail.verified, user.emailVerified ? texts.yes : texts.no],
                [texts.detail.status, user.enabled ? texts.options.active : texts.options.blocked],
                [texts.detail.createdAt, new Date(user.createdAt).toLocaleDateString()],
              ].map(([label, value]) => (
                <div className="detail-row" key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>

            {!canEditPanelUser && (
              <p className="helper-copy">{texts.selfProtection}</p>
            )}

            <div className="admin-detail-section">
              <span className="admin-detail-section-title">{texts.detailPanel.roleSection}</span>
              <label className="field-group">
                <span className="field-label">{texts.detail.role}</span>
                <select
                  className="field-input"
                  value={panel.pendingRole}
                  onChange={(e) => setPanel((p) => ({ ...p, pendingRole: e.target.value }))}
                  disabled={!canEditPanelUser || isPendingAction}
                >
                  <option value="ADMIN">{texts.roles.admin}</option>
                  <option value="PLAYER">{texts.roles.player}</option>
                </select>
              </label>
              <button
                type="button"
                className="primary-button"
                onClick={handleRoleSubmit}
                disabled={!canEditPanelUser || panel.pendingRole === user.role || isPendingAction}
              >
                {texts.buttons.saveRole}
              </button>
            </div>

            <div className="admin-detail-section">
              <span className="admin-detail-section-title">{texts.detailPanel.actionsSection}</span>
              <button
                type="button"
                className="secondary-button"
                onClick={handleStatusToggle}
                disabled={!canEditPanelUser || isPendingAction}
              >
                {user.enabled ? texts.buttons.block : texts.buttons.activate}
              </button>
            </div>
          </>
        )}
      </>
    );
  };

  // ── Main render ───────────────────────────────────────────────────

  return (
    <div className="admin-sidebar-layout">
      <ProfileSettings
        texts={profileTexts}
        knowledgeTexts={{ options: {} }}
        currentUser={currentUser}
        isOpen={isProfileOpen}
        onClose={() => setIsProfileOpen(false)}
        onUserUpdate={onUserUpdate}
      />

      {/* Backdrop for sliding panel */}
      {panel.isOpen && (
        <div
          className="admin-panel-backdrop"
          onClick={closePanel}
          role="presentation"
        />
      )}

      {/* Left sidebar */}
      <aside className="admin-sidebar">
        <div className="admin-sidebar-top">
          <p className="eyebrow">{texts.badge}</p>
          <div className="admin-sidebar-user-card">
            <div className="admin-user-avatar">
              {currentUser.profileImageUrl ? (
                <img src={currentUser.profileImageUrl} alt={profileTexts.photo.alt} />
              ) : (
                currentUser.name.charAt(0).toUpperCase()
              )}
            </div>
            <div className="admin-sidebar-user-info">
              <strong>{currentUser.name}</strong>
              <span className="status-pill active">
                {texts.roles[currentUser.role.toLowerCase()] || currentUser.role}
              </span>
            </div>
          </div>
        </div>

        <nav className="admin-nav">
          {Object.values(SECTIONS).map((id) => (
            <button
              key={id}
              type="button"
              className={`admin-nav-btn${activeSection === id ? ' admin-nav-btn-active' : ''}`}
              onClick={() => setActiveSection(id)}
            >
              <span className="admin-nav-icon" aria-hidden="true">{NAV_ICONS[id]}</span>
              {texts.nav[id]}
            </button>
          ))}
        </nav>

        <button
          type="button"
          className="admin-sidebar-logout text-link"
          onClick={() => setIsProfileOpen(true)}
        >
          {profileTexts.buttons.open}
        </button>

        <button
          type="button"
          className="admin-sidebar-logout text-link"
          onClick={onLogout}
        >
          {texts.buttons.logout}
        </button>
      </aside>

      {/* Main content area */}
      <main className="admin-main-content">
        {status.text && (
          <p
            className={`status-message ${status.isError ? 'error' : 'success'}`}
            aria-live="polite"
          >
            {status.text}
          </p>
        )}

        {activeSection === SECTIONS.USERS && renderUsersSection()}
        {activeSection === SECTIONS.STATS && renderStatsSection()}
        {activeSection === SECTIONS.ROLES && renderRolesSection()}
        {activeSection === SECTIONS.SIMULATIONS && renderSimulationsSection()}
      </main>

      {/* Sliding detail drawer */}
      <aside
        className={`admin-detail-drawer${panel.isOpen ? ' admin-detail-drawer-open' : ''}`}
        aria-hidden={!panel.isOpen}
      >
        {renderDetailPanel()}
      </aside>
    </div>
  );
}

export default AdminUserManagement;
