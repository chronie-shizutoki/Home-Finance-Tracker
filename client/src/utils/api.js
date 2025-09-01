import axios from 'axios';
// 导入axios但暂时不导入i18n以避免循环依赖问题

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
});

// 请求拦截器
api.interceptors.request.use(config => {
  config.signal = new AbortController().signal;
  return config;
}, error => {
  return Promise.reject(error);
});

// 响应拦截器
api.interceptors.response.use(
  response => response.data,
  error => {
    try {
      // 懒加载i18n以避免循环依赖
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
      // 如果i18n加载或使用失败，返回基本错误
      const fallbackError = new Error(error.response?.data?.error?.message || 'Network error');
      fallbackError.code = error.response?.status || 'NETWORK_ERROR';
      fallbackError.details = error.config;
      return Promise.reject(fallbackError);
    }
  }
);

export default api;
