import apiClient, { getErrorMessage } from './apiClient';

export const fetchUsers = async ({ name, email, enabled, role, page, size }) => {
  try {
    const response = await apiClient.get('/admin/users', {
      params: {
        name: name || undefined,
        email: email || undefined,
        enabled: enabled === '' ? undefined : enabled,
        role: role || undefined,
        page,
        size
      }
    });

    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};

export const fetchUserDetail = async (userId) => {
  try {
    const response = await apiClient.get(`/admin/users/${userId}`);
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};

export const updateUserStatus = async (userId, enabled) => {
  try {
    const response = await apiClient.patch(`/admin/users/${userId}/status`, { enabled });
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};

export const updateUserRole = async (userId, role) => {
  try {
    const response = await apiClient.patch(`/admin/users/${userId}/role`, { role });
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
};
