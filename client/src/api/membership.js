import axios from 'axios'
import { setupAxiosInterceptors } from '@/utils/offlineDataSync'

// Create axios instance for membership API requests
const apiClient = axios.create({
  baseURL: '/api/members',
  timeout: 30000
})

// Set interceptors
setupAxiosInterceptors(apiClient)

/**
 * Create or get user information
 * @param {Object} userData - User data
 * @param {string} userData.username - Username
 * @returns {Promise<Object>} User information
 */
export const getOrCreateUser = async (userData) => {
  try {
    const response = await apiClient.post('', userData)
    return response.data
  } catch (error) {
    console.error('Create or get user failed:', error)
    throw error
  }
}

/**
 * Update user avatar
 * @param {string} username - Username
 * @param {string} avatar - Avatar data (Base64 format)
 * @returns {Promise<Object>} Update result
 */
export const updateUserAvatar = async (username, avatar) => {
  try {
    const response = await apiClient.put(`/members/${username}/avatar`, { avatar })
    return response.data
  } catch (error) {
    console.error('Update avatar failed:', error)
    throw error
  }
}

export default {
  getOrCreateUser,
  updateUserAvatar
}