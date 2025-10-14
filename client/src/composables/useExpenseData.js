/*
* @file useExpenseData.js
* @package 家庭记账本
* @module 组合式函数
* @description 消费数据管理组合式函数，负责获取和处理消费记录数据
* @author 开发者
* @version 1.0
*/

import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ExpenseAPI } from '@/api/expenses';

/**
* 获取并管理消费数据的组合式函数
* @returns {Object} 包含消费数据和图表数据的响应式对象
*/
export function useExpenseData () {
  const { t } = useI18n();

  /**
   * 消费记录列表（响应式数据）
   * @type {import('vue').Ref<Array>}
   */
  const expenses = ref([]);

  /**
   * 数据获取错误信息（响应式数据）
   * @type {import('vue').Ref<string>}
   */
  const error = ref('');

  /**
   * 操作成功提示信息（响应式数据）
   * @type {import('vue').Ref<string>}
   */
  const successMessage = ref('');

  /**
   * 操作错误提示信息（响应式数据）
   * @type {import('vue').Ref<string>}
   */
  const errorMessage = ref('');

  /**
   * 获取消费数据
   * @param {boolean} forceRefresh - 是否强制刷新数据（目前未在外部使用，但保留）
   */
  const fetchData = async (forceRefresh = false) => {
    console.log('useExpenseData: fetchData called.');
    try {
      const res = await ExpenseAPI.getExpenses();
      // 确保 res.data.data 是一个数组，即使 API 返回 null 或 undefined
      const newData = res && res.data && res.data.data && Array.isArray(res.data.data) ? res.data.data : [];

      // 当强制刷新时直接更新数据，否则检查内容变化
      if (forceRefresh || JSON.stringify(expenses.value) !== JSON.stringify(newData)) {
        expenses.value = newData;
        console.log('useExpenseData: expenses.value updated' + (forceRefresh ? ' (force refresh)' : ' (content changed)') + '.');
      } else {
        console.log('useExpenseData: expenses.value content is identical, no update needed.');
      }

      error.value = ''; // 清除之前的错误信息
    } catch (err) {
      console.error('useExpenseData: 获取消费数据失败:', err.message || err);
      error.value = t('error.fetchExpensesFailed', { error: err.message || err });
      // 在错误情况下，确保 expenses 仍然是一个空数组，避免后续操作报错
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
