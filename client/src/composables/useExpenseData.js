/*
* @file useExpenseData.js
* @package Home Finance Tracker Client
* @module Composition Function
* @description Expense data management composition function, responsible for fetching and processing expense record data
* @author Developer
* @version 1.0
*/

import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ExpenseAPI } from '@/api/expenses';

/**
* Composition function to manage expense data, fetching and processing expense record data
* @returns {Object} Reactive object containing expense data and chart data
*/
export function useExpenseData () {
  const { t } = useI18n();

  /**
   * Expense record list (reactive data)
   * @type {import('vue').Ref<Array>}
   */
  const expenses = ref([]);

  /**
   * Data fetch error message (reactive data)
   * @type {import('vue').Ref<string>}
   */
  const error = ref('');

  /**
   * Operation success message (reactive data)
   * @type {import('vue').Ref<string>}
   */
  const successMessage = ref('');

  /**
   * Operation error message (reactive data)
   * @type {import('vue').Ref<string>}
   */
  const errorMessage = ref('');

  /**
   * Fetch expense data
   * @param {boolean} forceRefresh - Whether to force refresh data (currently not used externally, but kept for future extensions)
   */
  const fetchData = async (forceRefresh = false) => {
    console.log('useExpenseData: fetchData called.');
    try {
      const res = await ExpenseAPI.getExpenses();
      // Ensure res.data.data is an array, even if API returns null or undefined
      const newData = res && res.data && res.data.data && Array.isArray(res.data.data) ? res.data.data : [];

      // Update expenses.value if forceRefresh is true or content has changed
      if (forceRefresh || JSON.stringify(expenses.value) !== JSON.stringify(newData)) {
        expenses.value = newData;
        console.log('useExpenseData: expenses.value updated' + (forceRefresh ? ' (force refresh)' : ' (content changed)') + '.');
      } else {
        console.log('useExpenseData: expenses.value content is identical, no update needed.');
      }

      error.value = ''; // Clear previous error message if any exists
    } catch (err) {
      console.error('useExpenseData: fetchData failed:', err.message || err);
      error.value = t('error.fetchExpensesFailed', { error: err.message || err });
      // Ensure expenses is an array after error, to avoid subsequent operations throwing errors
      if (!Array.isArray(expenses.value)) {
        expenses.value = [];
      }
    }
  };

  return {
    expenses,
    error,
    fetchData,
    successMessage,
    errorMessage
  };
}
