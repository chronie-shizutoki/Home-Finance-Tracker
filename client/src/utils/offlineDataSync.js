import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { STORAGE_KEYS } from '@/utils/constants';
import { logApiRequest, logApiResponse, logApiError } from '@/utils/operationLogger';
import { ExpenseAPI } from '@/api/expenses';

/**
 * Offline data sync tool
 * Based on IndexedDB to store offline data and sync it when network is restored
 * Support UUID and version control
 */
class OfflineDataSync {
  constructor () {
    this.dbName = 'HomeMoneyDB';
    this.dbVersion = 2;
    this.stores = {
      cache: 'keyValueCache',
      syncQueue: 'syncQueue',
      expenses: 'expenses'
    };
    this.db = null;
    this.initPromise = null;
    this.initDB();
    this.setupNetworkListeners();
  }

  /**
   * Initialize IndexedDB database
   */
  async initDB () {
    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.dbVersion);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Create key-value cache store
        if (!db.objectStoreNames.contains(this.stores.cache)) {
          db.createObjectStore(this.stores.cache, { keyPath: 'key' });
        }

        // Create sync queue store
        if (!db.objectStoreNames.contains(this.stores.syncQueue)) {
          db.createObjectStore(this.stores.syncQueue, {
            keyPath: 'id',
            autoIncrement: true
          });
        }

        // Create expense store (support UUID primary key)
        if (!db.objectStoreNames.contains(this.stores.expenses)) {
          const expenseStore = db.createObjectStore(this.stores.expenses, {
            keyPath: 'id'
          });
          expenseStore.createIndex('updatedAt', 'updatedAt', { unique: false });
          expenseStore.createIndex('isSynced', 'isSynced', { unique: false });
          expenseStore.createIndex('deletedAt', 'deletedAt', { unique: false });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        console.error('IndexedDB initialization failed:', event.target.error);
        reject(event.target.error);
      };

      request.onblocked = (event) => {
        console.warn('IndexedDB blocked, please close other tabs');
        reject(new Error('IndexedDB blocked'));
      };
    });

    return this.initPromise;
  }

  /**
   * Ensure database is initialized
   */
  async ensureDB () {
    if (!this.db) {
      await this.initDB();
    }
    return this.db;
  }

  /**
   * Get database transaction
   */
  getTransaction (storeName, mode = 'readonly') {
    if (!this.db) {
      throw new Error('Database not initialized');
    }
    return this.db.transaction(storeName, mode).objectStore(storeName);
  }

  /**
   * Generate UUID
   */
  generateId () {
    return uuidv4();
  }

  /**
   * Save expense to local database (with version control)
   */
  async saveExpense (expense, isSynced = false) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    
    const expenseData = {
      id: expense.id || this.generateId(),
      ...expense,
      version: expense.version || 1,
      updatedAt: expense.updatedAt || Date.now(),
      isSynced: isSynced,
      deletedAt: expense.deletedAt || null
    };

    return new Promise((resolve, reject) => {
      const request = store.put(expenseData);
      request.onsuccess = () => resolve(expenseData);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Get expense from local database
   */
  async getExpense (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.get(id);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Get all pending changes
   */
  async getPendingChanges () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => {
        const allExpenses = request.result || [];
        const pending = allExpenses.filter(e => !e.isSynced);
        resolve(pending);
      };
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Get all active expenses from local database
   */
  async getAllExpenses () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => {
        const allExpenses = request.result || [];
        const active = allExpenses.filter(e => !e.deletedAt);
        active.sort((a, b) => b.updatedAt - a.updatedAt);
        resolve(active);
      };
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Mark expense as synced
   */
  async markAsSynced (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    return new Promise((resolve, reject) => {
      const getRequest = store.get(id);
      getRequest.onsuccess = () => {
        const expense = getRequest.result;
        if (expense) {
          expense.isSynced = true;
          const putRequest = store.put(expense);
          putRequest.onsuccess = () => resolve();
          putRequest.onerror = () => reject(putRequest.error);
        } else {
          resolve();
        }
      };
      getRequest.onerror = () => reject(getRequest.error);
    });
  }

  /**
   * Soft delete local expense record
   */
  async deleteExpense (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.expenses, 'readwrite');
    return new Promise((resolve, reject) => {
      const getRequest = store.get(id);
      getRequest.onsuccess = () => {
        const expense = getRequest.result;
        if (expense) {
          const now = Date.now();
          expense.deletedAt = now;
          expense.updatedAt = now;
          expense.version = (expense.version || 0) + 1;
          expense.isSynced = false;
          const putRequest = store.put(expense);
          putRequest.onsuccess = () => resolve(expense);
          putRequest.onerror = () => reject(putRequest.error);
        } else {
          resolve(null);
        }
      };
      getRequest.onerror = () => reject(getRequest.error);
    });
  }

  /**
   * Get last sync time from local storage
   */
  getLastSyncTime () {
    const time = localStorage.getItem('lastSyncTime');
    return time ? parseInt(time, 10) : 0;
  }

  /**
   * Set last sync time to local storage
   */
  setLastSyncTime (time) {
    localStorage.setItem('lastSyncTime', time.toString());
  }

  /**
   * Perform sync operation
   */
  async performSync () {
    if (!navigator.onLine) {
      console.log('Offline status, skip sync operation');
      return { success: false, offline: true };
    }

    try {
      await this.ensureDB();
      const lastSyncTime = this.getLastSyncTime();
      const pendingChanges = await this.getPendingChanges();

      console.log(`Start sync, last sync time: ${lastSyncTime}, pending changes: ${pendingChanges.length}`);

      const syncResult = await ExpenseAPI.syncExpenses(lastSyncTime, pendingChanges);

      if (syncResult.serverChanges && syncResult.serverChanges.length > 0) {
        for (const serverExpense of syncResult.serverChanges) {
          await this.saveExpense(serverExpense, true);
        }
        console.log(`Applied ${syncResult.serverChanges.length} server changes`);
      }

      if (syncResult.conflicts && syncResult.conflicts.length > 0) {
        console.warn(`Found ${syncResult.conflicts.length} conflicts, resolved by Last-Write-Wins`);
        for (const conflict of syncResult.conflicts) {
          await this.saveExpense(conflict.serverVersion, true);
        }
      }

      for (const change of pendingChanges) {
        await this.markAsSynced(change.id);
      }

      this.setLastSyncTime(syncResult.syncTime);

      console.log('Sync completed successfully');
      return {
        success: true,
        serverChanges: syncResult.serverChanges?.length || 0,
        conflicts: syncResult.conflicts?.length || 0
      };
    } catch (error) {
      console.error('Sync failed:', error);
      return { success: false, error: error.message };
    }
  }

  /**
   * Cache API response data
   */
  async cacheResponse (key, data) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.cache, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.put({ key, data, timestamp: Date.now() });
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Get cached response data
   */
  async getCachedResponse (key) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.cache);
    return new Promise((resolve, reject) => {
      const request = store.get(key);
      request.onsuccess = () => resolve(request.result?.data || null);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Add request to sync queue (compatible with old API)
   */
  async queueForSync (request) {
    await this.ensureDB();
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
   * Get all pending sync requests from queue
   */
  async getSyncQueue () {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.syncQueue);
    return new Promise((resolve, reject) => {
      const request = store.getAll();
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Remove synced request from queue
   */
  async removeFromSyncQueue (id) {
    await this.ensureDB();
    const store = this.getTransaction(this.stores.syncQueue, 'readwrite');
    return new Promise((resolve, reject) => {
      const request = store.delete(id);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Sync all requests in sync queue (compatible with old API)
   */
  async syncQueue () {
    if (!navigator.onLine) return;

    try {
      await this.ensureDB();
      const queue = await this.getSyncQueue();
      if (queue.length === 0) return;

      console.log(`Start sync ${queue.length} offline requests`);
      for (const request of queue) {
        try {
          const response = await axios(request);
          if (response.status >= 200 && response.status < 300) {
            await this.removeFromSyncQueue(request.id);
            console.log(`Successfully synchronized request: ${request.url}`);
          }
        } catch (error) {
          console.error(`Failed to synchronize request: ${request.url}`, error);
          break;
        }
      }
    } catch (error) {
      console.error('Sync queue processing failed', error);
    }
  }

  /**
   * Set network status listeners for sync
   */
  setupNetworkListeners () {
    window.addEventListener('online', () => {
      console.log('Network recovered, start sync');
      setTimeout(() => this.performSync(), 1000);
    });

    window.addEventListener('load', () => {
      if (navigator.onLine) {
        setTimeout(() => this.performSync(), 1000);
      }
    });
  }
}

const offlineSync = new OfflineDataSync();

export default offlineSync;

export function setupAxiosInterceptors (axiosInstance) {
  axiosInstance.interceptors.request.use(async (config) => {
    config.timestamp = Date.now();
    
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    logApiRequest(config);

    if (!navigator.onLine && ['post', 'put', 'delete', 'patch'].includes(config.method)) {
      console.log(`Offline mode: Queue request for sync - ${config.url}`);
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

  axiosInstance.interceptors.response.use(async (response) => {
    logApiResponse(response);
    
    if (response.config.method === 'get' && response.status === 200) {
      const cacheKey = `${response.config.method}-${response.config.url}`;
      await offlineSync.cacheResponse(cacheKey, response.data);
    }
    return response;
  }, async (error) => {
    logApiError(error);
    
    if (!navigator.onLine && error.config?.method === 'get') {
      const cacheKey = `${error.config.method}-${error.config.url}`;
      const cachedData = await offlineSync.getCachedResponse(cacheKey);
      if (cachedData) {
        console.log(`Offline mode: Use cached data - ${error.config.url}`);
        return Promise.resolve({ data: cachedData });
      }
    }
    return Promise.reject(error);
  });
}
