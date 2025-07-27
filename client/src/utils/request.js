import axios from 'axios';
import { STORAGE_KEYS } from '@/utils/constants';
// 直接使用相对路径作为基础URL，避免环境变量干扰

// 创建axios实例
const request = axios.create({
  baseURL: '/',
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 添加认证token
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    console.log('Request interceptor - Token found:', !!token);
    console.log('Token value:', token);
    // 仅在请求头中没有Authorization时添加令牌
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
      console.log('Authorization header set by interceptor:', config.headers.Authorization);
    } else if (!token) {
      console.log('No token available in localStorage');
    } else {
      console.log('Authorization header already present:', config.headers.Authorization);
    }
    console.log('Final request headers:', JSON.stringify(config.headers, null, 2));
    return config;
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem(STORAGE_KEYS.TOKEN);
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  response => {
    return response.data;
  },
  error => {
    return Promise.reject(error);
  }
);

export default request;
