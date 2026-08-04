import axios from 'axios';
// Import axios but not i18n to avoid circular dependencies

const apiBase = import.meta.env.VITE_API_BASE_URL || '/api';
const api = axios.create({
  baseURL: apiBase,
  timeout: 10000,
  withCredentials: true
});

// Request interceptor
api.interceptors.request.use(config => {
  config.signal = new AbortController().signal;
  return config;
}, error => {
  return Promise.reject(error);
});

// Response interceptor
api.interceptors.response.use(
  response => response.data,
  error => {
    try {
      // Lazy load i18n to avoid circular dependencies
      const i18n = require('@/locales/i18n.js').default;
      
      if (axios.isCancel(error)) {
        const cancelError = new Error(i18n?.global?.t ? i18n.global.t('expense.common.requestCanceled') : 'Request canceled');
        cancelError.code = 'REQUEST_CANCELED';
        return Promise.reject(cancelError);
      }
      
      const errorMessage = error.response?.data?.error?.message || 
                          (i18n?.global?.t ? i18n.global.t('expense.common.networkError') : 'Network error');
      
      const apiError = new Error(errorMessage);
      apiError.code = error.response?.status || 'NETWORK_ERROR';
      apiError.details = error.config;
      return Promise.reject(apiError);
    } catch (i18nError) {
      // If i18n loading or use fails, return basic error
      const fallbackError = new Error(error.response?.data?.error?.message || 'Network error');
      fallbackError.code = error.response?.status || 'NETWORK_ERROR';
      fallbackError.details = error.config;
      return Promise.reject(fallbackError);
    }
  }
);

export default api;
