import axios from 'axios';
import { STORAGE_KEYS } from '@/utils/constants';
// Use relative path as base URL to avoid environment variable interference

// Create axios instance with relative path as base URL
const request = axios.create({
  baseURL: '/',
  timeout: 10000, // Request timeout (ms)
  headers: {
    'Content-Type': 'application/json'
  }
});

// Request interceptor to add authentication token to request headers
request.interceptors.request.use(
  config => {
    // Add authentication token to request headers if available
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    console.log('Request interceptor - Token found:', !!token);
    console.log('Token value:', token);
    // Add token to request headers if not already present
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

// Response interceptor to handle response data
request.interceptors.response.use(
  response => {
    return response.data;
  },
  error => {
    return Promise.reject(error);
  }
);

export default request;
