const sessionStorageKey = 'toastedVrAdminSession';

export const readSession = () => {
  const rawSession = window.localStorage.getItem(sessionStorageKey);
  if (!rawSession) {
    return null;
  }

  try {
    return JSON.parse(rawSession);
  } catch (error) {
    window.localStorage.removeItem(sessionStorageKey);
    return null;
  }
};

export const saveSession = (session) => {
  window.localStorage.setItem(sessionStorageKey, JSON.stringify(session));
};

export const clearSession = () => {
  window.localStorage.removeItem(sessionStorageKey);
};
