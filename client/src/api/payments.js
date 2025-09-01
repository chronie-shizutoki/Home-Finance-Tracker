import axios from 'axios'

/**
 * 创建专门用于支付API的axios实例，先调用自己的服务器再由服务器代理调用第三方服务
 */
const paymentApi = axios.create({
  baseURL: 'http://192.168.0.197:3010/api',
  timeout: 10000,
  withCredentials: true
});

/**
 * 支付相关API
 * @module api/payments
 * @desc 提供捐赠等支付相关的API调用
 */

/**
 * 捐赠API
 * @function donate
 * @param {Object} data - 捐赠数据
 * @param {string} data.username - 用户名
 * @param {number} data.amount - 捐赠金额
 * @returns {Promise<Object>} 捐赠结果
 */
export const donate = async (data) => {
  try {
    const response = await paymentApi.post('/payments/donate', data)
    return response.data
  } catch (error) {
    console.error('捐赠API调用失败:', error)
    throw error
  }
}

/**
 * 获取捐赠历史记录
 * @function getDonationHistory
 * @param {Object} params - 查询参数
 * @param {number} params.page - 页码
 * @param {number} params.limit - 每页数量
 * @returns {Promise<Object>} 捐赠历史记录
 */
export const getDonationHistory = async (params = {}) => {
  try {
    const response = await paymentApi.get('/payments/history', { params })
    return response.data
  } catch (error) {
    console.error('获取捐赠历史记录失败:', error)
    throw error
  }
}

/**
 * 获取捐赠统计信息
 * @function getDonationStats
 * @returns {Promise<Object>} 捐赠统计信息
 */
export const getDonationStats = async () => {
  try {
    const response = await paymentApi.get('/payments/stats')
    return response.data
  } catch (error) {
    console.error('获取捐赠统计信息失败:', error)
    throw error
  }
}