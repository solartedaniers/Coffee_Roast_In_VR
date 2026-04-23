import React, { useEffect, useState } from 'react';
import {
  fetchUserDetail,
  fetchUsers,
  updateUserRole,
  updateUserStatus
} from '../services/adminUserService';

const defaultPageSize = 8;

const initialFilters = {
  name: '',
  email: '',
  enabled: '',
  role: ''
};

function AdminUserManagement({ texts, currentUser, onLogout }) {
  const [filters, setFilters] = useState(initialFilters);
  const [pageState, setPageState] = useState({
    number: 0,
    size: defaultPageSize,
    totalPages: 0,
    totalElements: 0
  });
  const [users, setUsers] = useState([]);
  const [selectedUserId, setSelectedUserId] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);
  const [pendingRole, setPendingRole] = useState('');
  const [isLoadingUsers, setIsLoadingUsers] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [status, setStatus] = useState({ text: '', isError: false });

  const loadUsers = async (nextPage = pageState.number, activeFilters = filters) => {
    setIsLoadingUsers(true);

    try {
      const response = await fetchUsers({
        ...activeFilters,
        page: nextPage,
        size: pageState.size
      });

      setUsers(response.content || []);
      setPageState((currentValue) => ({
        ...currentValue,
        number: response.number,
        totalPages: response.totalPages,
        totalElements: response.totalElements
      }));

      if (!selectedUserId && response.content?.length) {
        setSelectedUserId(response.content[0].id);
      }

      if (selectedUserId && !response.content?.some((user) => user.id === selectedUserId)) {
        setSelectedUserId(response.content?.[0]?.id ?? null);
      }
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsLoadingUsers(false);
    }
  };

  const loadUserDetail = async (userId) => {
    if (!userId) {
      setSelectedUser(null);
      setPendingRole('');
      return;
    }

    setIsLoadingDetail(true);

    try {
      const response = await fetchUserDetail(userId);
      setSelectedUser(response);
      setPendingRole(response.role);
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    } finally {
      setIsLoadingDetail(false);
    }
  };

  useEffect(() => {
    const loadInitialUsers = async () => {
      setIsLoadingUsers(true);

      try {
        const response = await fetchUsers({
          ...initialFilters,
          page: 0,
          size: defaultPageSize
        });

        setUsers(response.content || []);
        setPageState((currentValue) => ({
          ...currentValue,
          number: response.number,
          totalPages: response.totalPages,
          totalElements: response.totalElements
        }));
        setSelectedUserId(response.content?.[0]?.id ?? null);
      } catch (error) {
        setStatus({ text: error.message, isError: true });
      } finally {
        setIsLoadingUsers(false);
      }
    };

    loadInitialUsers();
  }, []);

  useEffect(() => {
    loadUserDetail(selectedUserId);
  }, [selectedUserId]);

  const handleFilterChange = (event) => {
    const { name, value } = event.target;
    setFilters((currentValue) => ({ ...currentValue, [name]: value }));
  };

  const handleApplyFilters = async (event) => {
    event.preventDefault();
    await loadUsers(0);
  };

  const handleResetFilters = async () => {
    const clearedFilters = { ...initialFilters };
    setFilters(clearedFilters);
    setStatus({ text: '', isError: false });
    setSelectedUserId(null);
    setSelectedUser(null);
    await loadUsers(0, clearedFilters);
  };

  const handleStatusToggle = async (user) => {
    try {
      const response = await updateUserStatus(user.id, !user.enabled);
      setStatus({ text: response.message, isError: false });
      await loadUsers(pageState.number);
      await loadUserDetail(user.id);
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    }
  };

  const handleRoleSubmit = async () => {
    if (!selectedUser || !pendingRole) {
      return;
    }

    try {
      const response = await updateUserRole(selectedUser.id, pendingRole);
      setStatus({ text: response.message, isError: false });
      await loadUsers(pageState.number);
      await loadUserDetail(selectedUser.id);
    } catch (error) {
      setStatus({ text: error.message, isError: true });
    }
  };

  const handlePageChange = async (direction) => {
    const nextPage = pageState.number + direction;
    if (nextPage < 0 || nextPage >= pageState.totalPages) {
      return;
    }

    await loadUsers(nextPage);
  };

  const canEditSelectedUser = selectedUser && selectedUser.id !== currentUser.id;
  const activeUsersCount = users.filter((user) => user.enabled).length;
  const blockedUsersCount = users.filter((user) => !user.enabled).length;
  const adminUsersCount = users.filter((user) => user.role === 'ADMIN').length;

  return (
    <section className="admin-layout">
      <header className="admin-header">
        <div className="admin-intro">
          <p className="eyebrow">{texts.badge}</p>
          <h1>{texts.title}</h1>
          <p className="subtitle">{texts.subtitle}</p>
        </div>

        <div className="admin-account-card">
          <p>{texts.accountLabel}</p>
          <strong>{currentUser.name}</strong>
          <span className="status-pill active">{texts.roles[currentUser.role.toLowerCase()] || currentUser.role}</span>
          <button type="button" className="secondary-button" onClick={onLogout}>
            {texts.buttons.logout}
          </button>
        </div>
      </header>

      <section className="admin-metrics-grid" aria-label={texts.listTitle}>
        <article className="metric-card">
          <span>{texts.metrics.total}</span>
          <strong>{pageState.totalElements}</strong>
        </article>
        <article className="metric-card">
          <span>{texts.metrics.active}</span>
          <strong>{activeUsersCount}</strong>
        </article>
        <article className="metric-card">
          <span>{texts.metrics.blocked}</span>
          <strong>{blockedUsersCount}</strong>
        </article>
        <article className="metric-card">
          <span>{texts.metrics.admins}</span>
          <strong>{adminUsersCount}</strong>
        </article>
      </section>

      <form className="filters-panel" onSubmit={handleApplyFilters}>
        <label className="field-group">
          <span className="field-label">{texts.filters.name}</span>
          <input
            className="field-input"
            type="text"
            name="name"
            value={filters.name}
            onChange={handleFilterChange}
            placeholder={texts.placeholders.name}
          />
        </label>

        <label className="field-group">
          <span className="field-label">{texts.filters.email}</span>
          <input
            className="field-input"
            type="email"
            name="email"
            value={filters.email}
            onChange={handleFilterChange}
            placeholder={texts.placeholders.email}
          />
        </label>

        <label className="field-group">
          <span className="field-label">{texts.filters.status}</span>
          <select className="field-input" name="enabled" value={filters.enabled} onChange={handleFilterChange}>
            <option value="">{texts.options.allStatuses}</option>
            <option value="true">{texts.options.active}</option>
            <option value="false">{texts.options.blocked}</option>
          </select>
        </label>

        <label className="field-group">
          <span className="field-label">{texts.filters.role}</span>
          <select className="field-input" name="role" value={filters.role} onChange={handleFilterChange}>
            <option value="">{texts.options.allRoles}</option>
            <option value="ADMIN">{texts.roles.admin}</option>
            <option value="PLAYER">{texts.roles.player}</option>
          </select>
        </label>

        <div className="filter-actions">
          <button className="primary-button" type="submit">{texts.buttons.search}</button>
          <button className="secondary-button" type="button" onClick={handleResetFilters}>
            {texts.buttons.clear}
          </button>
        </div>
      </form>

      {status.text && (
        <p className={`status-message ${status.isError ? 'error' : 'success'}`} aria-live="polite">
          {status.text}
        </p>
      )}

      <div className="admin-content-grid">
        <section className="panel-surface">
          <div className="panel-heading">
            <h2>{texts.listTitle}</h2>
            <span>{texts.resultsLabel.replace('{count}', pageState.totalElements)}</span>
          </div>

          <div className="users-table-wrapper">
            <table className="users-table">
              <thead>
                <tr>
                  <th>{texts.table.name}</th>
                  <th>{texts.table.email}</th>
                  <th>{texts.table.role}</th>
                  <th>{texts.table.status}</th>
                  <th>{texts.table.actions}</th>
                </tr>
              </thead>
              <tbody>
                {isLoadingUsers && (
                  <tr>
                    <td colSpan="5" className="empty-state">{texts.loadingUsers}</td>
                  </tr>
                )}

                {!isLoadingUsers && users.length === 0 && (
                  <tr>
                    <td colSpan="5" className="empty-state">{texts.empty}</td>
                  </tr>
                )}

                {!isLoadingUsers && users.map((user) => (
                  <tr
                    key={user.id}
                    className={user.id === selectedUserId ? 'is-selected' : ''}
                    onClick={() => setSelectedUserId(user.id)}
                  >
                    <td>{user.name}</td>
                    <td>{user.email}</td>
                    <td>{texts.roles[user.role.toLowerCase()] || user.role}</td>
                    <td>
                      <span className={`status-pill ${user.enabled ? 'active' : 'blocked'}`}>
                        {user.enabled ? texts.options.active : texts.options.blocked}
                      </span>
                    </td>
                    <td>
                      <button
                        type="button"
                        className="table-action-button"
                        onClick={(event) => {
                          event.stopPropagation();
                          handleStatusToggle(user);
                        }}
                        disabled={user.id === currentUser.id}
                      >
                        {user.enabled ? texts.buttons.block : texts.buttons.activate}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="pagination-row">
            <button
              type="button"
              className="secondary-button"
              onClick={() => handlePageChange(-1)}
              disabled={pageState.number === 0}
            >
              {texts.buttons.previous}
            </button>
            <span>
              {texts.pageLabel
                .replace('{page}', pageState.totalPages === 0 ? 0 : pageState.number + 1)
                .replace('{totalPages}', pageState.totalPages)}
            </span>
            <button
              type="button"
              className="secondary-button"
              onClick={() => handlePageChange(1)}
              disabled={pageState.number + 1 >= pageState.totalPages}
            >
              {texts.buttons.next}
            </button>
          </div>
        </section>

        <aside className="panel-surface detail-panel">
          <div className="panel-heading">
            <h2>{texts.detailTitle}</h2>
            {selectedUser && <span>{texts.selectedLabel}</span>}
          </div>

          {isLoadingDetail && <p className="empty-state">{texts.loadingDetail}</p>}

          {!isLoadingDetail && !selectedUser && (
            <p className="empty-state">{texts.emptyDetail}</p>
          )}

          {!isLoadingDetail && selectedUser && (
            <div className="detail-stack">
              <div className="detail-row">
                <span>{texts.detail.name}</span>
                <strong>{selectedUser.name}</strong>
              </div>
              <div className="detail-row">
                <span>{texts.detail.email}</span>
                <strong>{selectedUser.email}</strong>
              </div>
              <div className="detail-row">
                <span>{texts.detail.username}</span>
                <strong>{selectedUser.username}</strong>
              </div>
              <div className="detail-row">
                <span>{texts.detail.verified}</span>
                <strong>{selectedUser.emailVerified ? texts.yes : texts.no}</strong>
              </div>
              <div className="detail-row">
                <span>{texts.detail.status}</span>
                <strong>{selectedUser.enabled ? texts.options.active : texts.options.blocked}</strong>
              </div>
              <div className="detail-row">
                <span>{texts.detail.createdAt}</span>
                <strong>{new Date(selectedUser.createdAt).toLocaleString()}</strong>
              </div>

              <label className="field-group">
                <span className="field-label">{texts.detail.role}</span>
                <select
                  className="field-input"
                  value={pendingRole}
                  onChange={(event) => setPendingRole(event.target.value)}
                  disabled={!canEditSelectedUser}
                >
                  <option value="ADMIN">{texts.roles.admin}</option>
                  <option value="PLAYER">{texts.roles.player}</option>
                </select>
              </label>

              <div className="detail-actions">
                <button
                  type="button"
                  className="primary-button"
                  onClick={handleRoleSubmit}
                  disabled={!canEditSelectedUser || pendingRole === selectedUser.role}
                >
                  {texts.buttons.saveRole}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => handleStatusToggle(selectedUser)}
                  disabled={!canEditSelectedUser}
                >
                  {selectedUser.enabled ? texts.buttons.block : texts.buttons.activate}
                </button>
              </div>

              {!canEditSelectedUser && (
                <p className="helper-copy">{texts.selfProtection}</p>
              )}
            </div>
          )}
        </aside>
      </div>
    </section>
  );
}

export default AdminUserManagement;
