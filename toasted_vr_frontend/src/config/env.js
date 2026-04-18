const defaultApiBaseUrl = 'http://localhost:8081/api/v1';

export const apiBaseUrl = process.env.REACT_APP_API_BASE_URL || defaultApiBaseUrl;
