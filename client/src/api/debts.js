import axios from 'axios';

// 使用相对路径API基础URL，通过Vite代理转发请求
const API_BASE = '/api';

export const DebtAPI = {
  /**
   * 获取债务记录列表
   * @param {Object} params 查询参数
   * @param {number} params.page 页码
   * @param {number} params.limit 每页数量
   * @param {string} params.type 债务类型：'lend'（借出）或 'borrow'（借入）
   * @param {boolean} params.isRepaid 是否已还款
   * @returns {Promise<Array>} 债务记录列表
   */
  async getDebts (params = {}) {
    try {
      const response = await axios.get(`${API_BASE}/debts`, {
        params: {
          page: params.page || 1,
          limit: params.limit || 1000000,
          type: params.type,
          isRepaid: params.isRepaid
        }
      });
      
      // 适配API响应格式
      if (response && response.data && response.data.data && Array.isArray(response.data.data)) {
        return response.data.data;
      }
      return [];
    } catch (error) {
      console.error('获取债务记录失败:', error);
      throw error;
    }
  },

  /**
   * 添加新的债务记录
   * @param {Object} data 债务数据
   * @param {string} data.type 债务类型：'lend'（借出）或 'borrow'（借入）
   * @param {string} data.person 交易对方姓名
   * @param {number} data.amount 交易金额
   * @param {string|Date} data.date 交易日期
   * @param {boolean} data.isRepaid 是否已还款
   * @param {string} data.remark 备注
   * @returns {Promise<Object>} 添加的债务记录
   */
  async addDebt (data) {
    try {
      return await axios.post(`${API_BASE}/debts`, data, {
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        transformRequest: [(debtData) => JSON.stringify({
          ...debtData,
          amount: parseFloat(debtData.amount)
        })]
      });
    } catch (error) {
      console.error('添加债务记录失败:', error);
      throw error;
    }
  },

  /**
   * 更新债务记录
   * @param {string|number} id 债务记录ID
   * @param {Object} data 更新的债务数据
   * @returns {Promise<Object>} 更新后的债务记录
   */
  async updateDebt (id, data) {
    try {
      return await axios.put(`${API_BASE}/debts/${id}`, data, {
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        transformRequest: [(debtData) => JSON.stringify({
          ...debtData,
          amount: debtData.amount !== undefined ? parseFloat(debtData.amount) : undefined
        })]
      });
    } catch (error) {
      console.error('更新债务记录失败:', error);
      throw error;
    }
  },

  /**
   * 删除债务记录
   * @param {string|number} id 债务记录ID
   * @returns {Promise<void>}
   */
  async deleteDebt (id) {
    try {
      await axios.delete(`${API_BASE}/debts/${id}`, {
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
    } catch (error) {
      console.error('删除债务记录失败:', error);
      throw error;
    }
  }
};