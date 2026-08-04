import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

// Relative path API base URL, forwarded by Vite proxy to handle CORS issues
export const API_BASE = '/api';

// Generate UUID for each record
export const generateId = () => uuidv4();

export const ExpenseAPI = {
  async addExpensesBatch (records) {
    try {
      // Add UUID and version to each record
      const recordsWithMeta = records.map(record => ({
        id: record.id || generateId(),
        ...record,
        version: record.version || 1,
        updatedAt: record.updatedAt || Date.now()
      }));
      return await axios.post(`${API_BASE}/expenses/batch`, recordsWithMeta, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
    } catch (error) {
      console.error('[Expense API] Batch add expenses failed with error:', error);
      throw error;
    }
  },

  async getExpenses (page = 1, limit = 10, searchParams = {}) {
    console.log('[Expense API] Trying to get expenses data from API base URL:', API_BASE);
    try {
      // Build query parameters
      let params;
      
      // Handle URLSearchParams object or plain object
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // Add basic pagination parameters
        params.page = page;
        params.limit = limit;
        
        // Extract all parameters from URLSearchParams
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // Handle plain object case
        params = {
          page,
          limit,
          ...searchParams
        };
      }
      
      // Log request parameters for debugging
      console.log('[Expense API] Request parameters:', params);
      const response = await axios.get(`${API_BASE}/expenses`, {
        params
      });
      
      // Return full response object, including data, total, page, etc
      return response;
    } catch (error) {
      if (error.code === 'ERR_NETWORK') {
        console.error('[Expense API] Failed to get expenses data: Network connection error, please check server or network status.', error);
      } else {
        console.error('[Expense API] Failed to get expenses data:', error);
      }
      console.error('[Expense API] Failed to get expenses data details:', error.response || error.message || error);
      throw error; // Propagate error to frontend for handling
    }
  },

  async addExpense (data) {
    try {
      const expenseData = {
        id: data.id || generateId(),
        ...data,
        amount: parseFloat(data.amount),
        date: data.date,
        remark: data.remark || '',
        version: data.version || 1,
        updatedAt: data.updatedAt || Date.now()
      };
      return await axios.post(`${API_BASE}/expenses`, expenseData, {
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
    } catch (error) {
      console.error('[Expense API] Failed to add expense data:', error);
      throw error; // Propagate error to frontend for handling
    }
  },

  // Sync API for offline synchronization
  async syncExpenses (lastSyncTime, changes) {
    try {
      const response = await axios.post(`${API_BASE}/expenses/sync`, {
        lastSyncTime,
        changes
      }, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('[Expense API] Failed to sync expenses data:', error);
      throw error; // Propagate error to frontend for handling
    }
  },

  async getStatistics (searchParams = {}) {
    try {
      // Handle URLSearchParams object or plain object
      let params;
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // Extract all parameters from URLSearchParams
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // Handle plain object case
        params = { ...searchParams };
      }
      
      console.log('[Expense API] Request parameters for getStatistics:', params);
      const response = await axios.get(`${API_BASE}/expenses/statistics`, {
        params
      });
      return response.data;
    } catch (error) {
      console.error('[Expense API] Failed to get statistics data:', error);
      return { error: error.message || 'Unknown error' };
    }
  },

  // Update expense record
  async updateExpense (id, data) {
    try {
      console.log(`[Expense API] Update expense record ID: ${id}`, data);
      const updateData = {
        ...data,
        amount: parseFloat(data.amount),
        date: data.date,
        version: (data.version || 0) + 1,
        updatedAt: Date.now()
      };
      const response = await axios.put(`${API_BASE}/expenses/${id}`, updateData, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error(`[Expense API] Failed to update expense record ID: ${id}:`, error);
      throw error;
    }
  },

  // Delete expense record
  async deleteExpense (id) {
    try {
      console.log(`[Expense API] Delete expense record ID: ${id}`);
      const response = await axios.delete(`${API_BASE}/expenses/${id}`);
      return response.data;
    } catch (error) {
      console.error(`Delete expense record ID: ${id}:`, error);
      throw error;
    }
  },

  // Get expense records grouped by date (for daily statistics)
  async getExpensesByDate (page = 1, limit = 10, searchParams = {}) {
    console.log('[Expense API] Request to get expense records grouped by date');
    try {
      // Build query parameters
      let params;
      
      // Handle URLSearchParams object or plain object
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // Add basic pagination parameters
        params.page = page;
        params.limit = limit;
        
        // Extract all parameters from URLSearchParams
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // Handle plain object case
        params = {
          page,
          limit,
          ...searchParams
        };
      }
      
      // Log request parameters for debugging
      console.log('[Expense API] Request parameters:', params);
      const response = await axios.get(`${API_BASE}/expenses/by-date`, {
        params
      });
      
      // Return complete response object, including data, total count, page, etc.
      return response;
    } catch (error) {
      if (error.code === 'ERR_NETWORK') {
        console.error('Failed to get expense records grouped by date: Network connection error, please check server or network status.', error);
      } else {
        console.error('Failed to get expense records grouped by date:', error);
      }
      console.error('[Expense API] Failed to get expense records grouped by date details:', error.response || error.message || error);
      throw error;
    }
  }
};