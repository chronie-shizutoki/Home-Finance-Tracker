import axios from 'axios';
import { STORAGE_KEYS } from '@/utils/constants';
import { logApiRequest, logApiResponse, logApiError } from '@/utils/operationLogger';

/**
 * 离线数据同步工具
 * 基于IndexedDB实现离线数据存储和网络恢复后的自动同步
 */
class OfflineDataSync {
  constructor () {
    this.dbName = 'HomeMoneyDB';
    this.dbVersion = 1;
    this.stores = {
      cache: 'keyValueCache', // 键值对缓存存储
      syncQueue: 'syncQueue' // 待同步请求队列
    };
    this.db = null;
    this.initDB();
    this.setupNetworkListeners();
  }

  /**
   * 初始化IndexedDB数据库
   */
  async initDB () {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.dbVersion);

      request.onupgradeneeded = (event) => {
        this.db = event.target.result;

        // 创建键值对缓存存储
        if (!this.db.objectStoreNames.contains(this.stores.cache)) {
          this.db.createObjectStore(this.stores.cache, { keyPath: 'key' });
        }

        // 创建同步队列存储（按时间戳排序）
        if (!this.db.objectStoreNames.contains(this.stores.syncQueue)) {
          this.db.createObjectStore(this.stores.syncQueue, {
            keyPath: 'id',
            autoIncrement: true
          });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        console.error('IndexedDB初始化失败:', event.target.error);
        reject(event.target.error);
      };
    });
  }

  /**
   * 获取数据库事务
   */
  getTransaction (storeName, mode = 'readonly') {
    if (!this.db) return null;
    return this.db.transaction(storeName, mode).objectStore(storeName);
  }

  /**
   * 缓存API响应数据
   */
  async cacheResponse (key, data) {
    if (!this.db) await this.initDB();
    const store = this.getTransaction(this.stores.cache, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.put({ key, data, timestamp: Date.now() });
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 获取缓存的响应数据
   */
  async getCachedResponse (key) {
    if (!this.db) await this.initDB();
    const store = this.getTransaction(this.stores.cache);
    return new Promise((resolve, reject) => {
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result?.data || null);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 将请求添加到同步队列
   */
  async queueForSync (request) {
    if (!this.db) await this.initDB();
    const store = this.getTransaction(this.stores.syncQueue, 'readwrite');
    return new Promise((resolve, reject) => {
      const requestData = {
        ...request,
        timestamp: Date.now()
      };
      const dbRequest = store.add(requestData);
      dbRequest.onsuccess = () => resolve(dbRequest.result);
      dbRequest.onerror = () => reject(dbRequest.error);
    });
  }

  /**
   * 获取所有待同步请求
   */
  async getSyncQueue () {
    if (!this.db) await this.initDB();
    const store = this.getTransaction(this.stores.syncQueue);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 移除已同步的请求
   */
  async removeFromSyncQueue (id) {
    if (!this.db) await this.initDB();
    const store = this.getTransaction(this.stores.syncQueue, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.delete(id);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * 同步队列中的所有请求
   */
  async syncQueue () {
    if (!navigator.onLine) return;

    try {
      const queue = await this.getSyncQueue();
      if (queue.length === 0) return;

      console.log(`开始同步${queue.length}个离线请求`);
      for (const request of queue) {
        try {
          // 重新发送请求
          const response = await axios(request);
          if (response.status >= 200 && response.status < 300) {
            await this.removeFromSyncQueue(request.id);
            console.log(`已同步请求: ${request.url}`);
          }
        } catch (error) {
          console.error(`同步请求失败: ${request.url}`, error);
          // 保留失败的请求，等待下次同步
          break;
        }
      }
    } catch (error) {
      console.error('同步队列处理失败', error);
    }
  }

  /**
   * 设置网络状态监听
   */
  setupNetworkListeners () {
    // 网络恢复时同步队列
    window.addEventListener('online', () => this.syncQueue());

    // 页面加载时检查同步队列
    window.addEventListener('load', () => this.syncQueue());
  }
}

// 创建单例实例
const offlineSync = new OfflineDataSync();

export default offlineSync;

export function setupAxiosInterceptors (axiosInstance) {
  // 请求拦截器 - 添加认证令牌并处理离线请求
  axiosInstance.interceptors.request.use(async (config) => {
    // 添加请求时间戳
    config.timestamp = Date.now();
    
    // 添加认证令牌
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 记录API请求日志
    logApiRequest(config);

    // 处理离线POST/PUT/DELETE请求
    if (!navigator.onLine && ['post', 'put', 'delete', 'patch'].includes(config.method)) {
      console.log(`离线模式: 将请求加入同步队列 - ${config.url}`);
      await offlineSync.queueForSync({
        url: config.url,
        method: config.method,
        data: config.data,
        headers: config.headers
      });
      return Promise.reject(new Error('OFFLINE_MODE'));
    }
    return config;
  });

  // 响应拦截器 - 缓存GET请求结果
  axiosInstance.interceptors.response.use(async (response) => {
    // 记录API响应日志
    logApiResponse(response);
    
    if (response.config.method === 'get' && response.status === 200) {
      const cacheKey = `${response.config.method}-${response.config.url}`;
      await offlineSync.cacheResponse(cacheKey, response.data);
    }
    return response;
  }, async (error) => {
    // 记录API错误日志
    logApiError(error);
    
    // 请求失败且离线时，尝试从缓存获取GET请求数据
    if (!navigator.onLine && error.config?.method === 'get') {
      const cacheKey = `${error.config.method}-${error.config.url}`;
      const cachedData = await offlineSync.getCachedResponse(cacheKey);
      if (cachedData) {
        console.log(`离线模式: 使用缓存数据 - ${error.config.url}`);
        return Promise.resolve({ data: cachedData });
      }
    }
    return Promise.reject(error);
  });
}
