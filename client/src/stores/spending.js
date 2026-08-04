import { defineStore } from 'pinia';

const isValidDate = (date) => {
  return date instanceof Date && !isNaN(date.getTime());
};

const formatDate = (date) => {
  if (!isValidDate(date)) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const formatMonth = (date) => {
  if (!isValidDate(date)) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
};

const parseDate = (dateString) => {
  if (!dateString) return null;
  const date = new Date(dateString);
  return isValidDate(date) ? date : null;
};

export const useSpendingStore = defineStore('spending', {
  state: () => ({
    monthlyLimit: 0, // Monthly spending limit
    currentMonthSpending: 0, // Current month spending total
    isLimitEnabled: false, // 是否启用限制功能
    lastCalculatedMonth: '', // Last calculated month, used for cache optimization
    warningThreshold: 0.8, // Warning threshold (80%)
    expenses: [] // Expense record cache
  }),

  getters: {
    // Calculate spending percentage (no max limit, for real-time display)
    spendingPercentage: (state) => {
      if (!state.monthlyLimit || state.monthlyLimit <= 0) return 0;
      return (state.currentMonthSpending / state.monthlyLimit) * 100;
    },

    // Remaining amount that can be spent
    remainingAmount: (state) => {
      return Math.max(state.monthlyLimit - state.currentMonthSpending, 0);
    },

    // Whether spending exceeds the limit
    isOverLimit: (state) => {
      return state.isLimitEnabled && state.currentMonthSpending > state.monthlyLimit;
    },

    // Whether spending is near the limit (reached warning threshold)
    isNearLimit: (state) => {
      return state.isLimitEnabled &&
             state.currentMonthSpending >= (state.monthlyLimit * state.warningThreshold) &&
             !state.isOverLimit;
    },

    // Get current status type
    statusType: (state) => {
      if (!state.isLimitEnabled) return 'disabled';
      if (state.isOverLimit) return 'danger';
      if (state.isNearLimit) return 'warning';
      return 'normal';
    },

    // Get status color
    statusColor: (state) => {
      switch (state.statusType) {
      case 'danger': return '#f56c6c';
      case 'warning': return '#e6a23c';
      case 'normal': return '#67c23a';
      default: return '#909399';
      }
    },

    // Current month string
    currentMonth: () => {
      return formatMonth(new Date());
    }
  },

  actions: {
    // Set monthly spending limit
    setMonthlyLimit (limit) {
      this.monthlyLimit = Math.max(0, Number(limit) || 0);
      this.saveSettings();
    },

    // Enable/disable spending limit
    toggleLimitEnabled (enabled) {
      this.isLimitEnabled = Boolean(enabled);
      this.saveSettings();
    },

    // Set warning threshold (between 0.1 and 1)
    setWarningThreshold (threshold) {
      this.warningThreshold = Math.max(0.1, Math.min(1, Number(threshold) || 0.8));
      this.saveSettings();
    },

    // Update expense records and recalculate current month spending
    updateExpenses (expenses) {
      this.expenses = expenses || [];
      this.calculateCurrentMonthSpending();
    },

    // Calculate current month spending total
    calculateCurrentMonthSpending () {
      const currentMonth = this.currentMonth;

      // If it's the same month and already calculated, return cached result
      if (this.lastCalculatedMonth === currentMonth && this.currentMonthSpending > 0) {
        return this.currentMonthSpending;
      }

      // Calculate current month's spending total
      const monthlyTotal = this.expenses
        .filter(expense => {
          // Use date field instead of time field, consistent with backend data
          // Keep backward compatibility, check date field first, then time field
          const expenseDate = expense.date || expense.time;
          if (!expenseDate) return false;
          const parsedDate = parseDate(expenseDate);
          if (!parsedDate) return false;
          const expenseMonth = formatMonth(parsedDate);
          return expenseMonth === currentMonth;
        })
        .reduce((total, expense) => {
          const amount = Number(expense.amount) || 0;
          return total + Math.abs(amount); // Use absolute value to ensure all positive numbers
        }, 0);

      this.currentMonthSpending = monthlyTotal;
      this.lastCalculatedMonth = currentMonth;

      return monthlyTotal;
    },

    // Add new expense record
    addExpense (expense) {
      // Use date field instead of time field, keep backward compatibility
      if (expense && expense.amount && (expense.date || expense.time)) {
        this.expenses.push(expense);
        this.calculateCurrentMonthSpending();
      }
    },

    // Get spending status information
    getSpendingStatus () {
      if (!this.isLimitEnabled) {
        return {
          type: 'info',
          message: 'spending.status.disabled',
          showProgress: false
        };
      }

      const percentage = this.spendingPercentage;
      const remaining = this.remainingAmount;

      if (this.isOverLimit) {
        const overAmount = this.currentMonthSpending - this.monthlyLimit;
        return {
          type: 'danger',
          message: 'spending.status.overLimit',
          data: { overAmount, percentage: Math.round(percentage) },
          showProgress: true
        };
      }

      if (this.isNearLimit) {
        return {
          type: 'warning',
          message: 'spending.status.nearLimit',
          data: { remaining, percentage: Math.round(percentage) },
          showProgress: true
        };
      }

      return {
        type: 'success',
        message: 'spending.status.normal',
        data: { remaining, percentage: Math.round(percentage) },
        showProgress: true
      };
    },

    // Save settings to local storage
    saveSettings () {
      try {
        const settings = {
          monthlyLimit: this.monthlyLimit,
          isLimitEnabled: this.isLimitEnabled,
          warningThreshold: this.warningThreshold
        };
        localStorage.setItem('homemoney-spending-settings', JSON.stringify(settings));
      } catch (error) {
        console.error('Failed to save spending settings:', error);
      }
    },

    // Load spending settings from local storage
    loadSettings () {
      try {
        const saved = localStorage.getItem('homemoney-spending-settings');
        if (saved) {
          const settings = JSON.parse(saved);
          this.monthlyLimit = Number(settings.monthlyLimit) || 0;
          this.isLimitEnabled = Boolean(settings.isLimitEnabled);
          this.warningThreshold = Number(settings.warningThreshold) || 0.8;
        }
      } catch (error) {
        console.error('Failed to load spending settings:', error);
        // Use default values
        this.monthlyLimit = 0;
        this.isLimitEnabled = false;
        this.warningThreshold = 0.8;
      }
      this.fetchExpenses();
    },

    // Reset all settings
    resetSettings () {
      this.monthlyLimit = 0;
      this.isLimitEnabled = false;
      this.warningThreshold = 0.8;
      this.currentMonthSpending = 0;
      this.lastCalculatedMonth = '';
      this.saveSettings();
    },

    // Fetch expense data from backend API and update local state
    async fetchExpenses () {
      try {
        console.log('Fetching expenses from API...');
        const response = await fetch('/api/expenses?limit=1000'); // Get more data for calculation
        console.log('Expenses API response status:', response.status);
        if (!response.ok) throw new Error(`Failed to fetch expenses: ${response.statusText}`);
        const result = await response.json();
        console.log('Fetched expenses data:', result);
        
        // Adapt new API response format
        let expenses = [];
        if (result && result.data && Array.isArray(result.data)) {
          expenses = result.data;
        } else if (Array.isArray(result)) {
          expenses = result;
        }
        
        this.updateExpenses(expenses);
        console.log('Updated expenses - currentMonthSpending:', this.currentMonthSpending);
      } catch (error) {
        console.error('Error fetching expenses:', error);
      }
    }
  }
});
