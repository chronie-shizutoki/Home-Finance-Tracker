<template>
  <div class="donation-page-wrapper">
    <div class="donation-container">
      <div class="donation-header">
        <div class="header-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="#409eff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="#409eff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="#409eff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1>支持我们的项目</h1>
        <p class="subtitle">您的每一份捐赠都将帮助我们持续改进和发展</p>
      </div>
      
      <div class="donation-form card-shadow">
        <el-form :model="donationForm" :rules="donationRules" ref="donationFormRef" label-width="100px">
          <el-form-item label="用户名" prop="username" class="form-item-custom">
            <el-input 
              v-model="donationForm.username" 
              placeholder="请输入用户名" 
              class="custom-input"
              prefix-icon="User"
            />
          </el-form-item>
        
          <el-form-item label="金额" prop="amount" class="form-item-custom">
            <div class="amount-options">
              <el-button 
                v-for="option in amountOptions" 
                :key="option" 
                type="primary" 
                plain
                :class="['amount-btn', { active: donationForm.amount === option }]"
                @click="selectAmount(option)"
              >
                {{ option }} 元
              </el-button>
            </div>
            <div class="custom-amount-wrapper">
              <span class="currency-symbol">¥</span>
              <el-input 
                v-model="donationForm.amount" 
                type="number" 
                placeholder="自定义金额"
                class="custom-amount-input"
                min="0" 
                step="0.01"
              />
            </div>
          </el-form-item>
        
          <el-form-item class="submit-button-container">
            <el-button 
              type="primary" 
              @click="handleDonate" 
              :loading="isSubmitting"
              class="submit-donation-btn"
              :icon="isSubmitting ? 'Loading' : ''"
            >
              {{ isSubmitting ? '处理中...' : '确认捐赠' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      
      <div v-if="donationResult" class="donation-result animate-fade-in">
        <el-alert 
          :title="donationResult.success ? '捐赠成功' : '捐赠失败'"
          :type="donationResult.success ? 'success' : 'error'"
          closable
          @close="resetDonationResult"
          class="result-alert"
        >
          <template #default>
            <p v-if="donationResult.success" class="success-message">
              非常感谢您的慷慨捐赠，金额为 <span class="donation-amount-highlight">{{ donationForm.amount }} 元</span>
            </p>
            <p v-else class="error-message">
              {{ donationResult.error }}
            </p>
            <p v-if="donationResult.data && donationResult.data.orderId" class="transaction-id">
              订单号: <code>{{ donationResult.data.orderId }}</code>
            </p>
          </template>
        </el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { donate } from '@/api/payments'

// Define donation form data
const donationForm = reactive({
  username: '',
  amount: ''
})

// Define donation form validation rules
const donationRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' }
  ],
  amount: [
    { required: true, message: '金额不能为空', trigger: 'blur' },
    { 
      type: 'number', 
      message: '金额必须为数字', 
      trigger: 'blur',
      transform: value => parseFloat(value)
    },
    { 
      validator: (rule, value, callback) => {
        if (value <= 0) {
          callback(new Error('金额必须为正数'))
        } else {
          // Validate maximum two decimal places
          const decimalPart = value.toString().split('.')[1]
          if (decimalPart && decimalPart.length > 2) {
            callback(new Error('金额最多保留两位小数'))
          } else {
            callback()
          }
        }
      },
      trigger: 'blur'
    }
  ]
}

// Define amount options
const amountOptions = [2000, 4000, 6000, 8000, 10000]

// Form reference
const donationFormRef = ref(null)

// Submission status
const isSubmitting = ref(false)

// Donation result
const donationResult = ref(null)

// Select amount
const selectAmount = (amount) => {
  donationForm.amount = amount
}

// Handle donation
const handleDonate = async () => {
  if (!donationFormRef.value) return
  
  try {
    // Validate form
    await donationFormRef.value.validate()
    
    // Set submission status
    isSubmitting.value = true
    
    // Call donation API
    const result = await donate({
      username: donationForm.username,
      amount: parseFloat(donationForm.amount)
    })
    
    // Handle donation result
    donationResult.value = result
    
    if (result.success) {
      ElMessage.success('捐赠成功')
    } else {
      ElMessage.error('捐赠失败')
    }
  } catch (error) {
    console.error('捐赠失败:', error)
    ElMessage.error('捐赠失败')
  } finally {
    // Reset submission status
    isSubmitting.value = false
  }
}

// Reset donation result
const resetDonationResult = () => {
  donationResult.value = null
}
</script>

<style scoped>
/* 基础样式重置和动画定义 */
:deep(*) {
  box-sizing: border-box;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0% { transform: scale(1); }
  50% { transform: scale(1.02); }
  100% { transform: scale(1); }
}

@keyframes gradientBackground {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

/* 页面容器 */
.donation-page-wrapper {
  min-height: 100vh;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-size: 400% 400%;
  animation: gradientBackground 15s ease infinite;
  display: flex;
  align-items: center;
  justify-content: center;
}

.donation-container {
  max-width: 600px;
  width: 100%;
  background-color: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  animation: fadeIn 0.6s ease-out;
}

/* 头部样式 */
.donation-header {
  text-align: center;
  margin-bottom: 40px;
}

.header-icon {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
  animation: pulse 2s infinite;
}

.donation-header h1 {
  color: #2c3e50;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: -0.5px;
}

.subtitle {
  color: #7f8c8d;
  font-size: 16px;
  line-height: 1.5;
  max-width: 400px;
  margin: 0 auto;
}

/* 表单样式 */
.donation-form {
  width: 100%;
}

.card-shadow {
  background: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  border: 1px solid #f0f0f0;
}

.form-item-custom {
  margin-bottom: 24px;
}

.form-item-custom .el-form-item__label {
  font-weight: 600;
  color: #555;
  font-size: 14px;
}

.custom-input {
  width: 100%;
  transition: all 0.3s ease;
}

.custom-input:focus-within {
  transform: translateY(-2px);
}

/* 金额选择按钮 */
.amount-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.amount-btn {
  transition: all 0.3s ease;
  border-radius: 8px !important;
  font-weight: 600 !important;
  height: 44px !important;
  font-size: 14px !important;
  background: #f8f9fa !important;
  border-color: #dee2e6 !important;
  color: #495057 !important;
}

.amount-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2) !important;
  border-color: #409eff !important;
  color: #409eff !important;
}

.amount-btn.active {
  background: #409eff !important;
  border-color: #409eff !important;
  color: #fff !important;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3) !important;
}

/* 自定义金额输入 */
.custom-amount-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.currency-symbol {
  position: absolute;
  left: 15px;
  font-size: 16px;
  font-weight: 600;
  color: #666;
  z-index: 1;
}

.custom-amount-input {
  width: 100%;
  padding-left: 30px !important;
  height: 44px !important;
  border-radius: 8px !important;
  font-size: 16px !important;
  transition: all 0.3s ease;
}

.custom-amount-input:focus {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2) !important;
}

/* 提交按钮 */
.submit-button-container {
  display: flex;
  justify-content: center;
  margin-top: 32px !important;
}

.submit-donation-btn {
  width: 100%;
  max-width: 200px;
  height: 48px;
  border-radius: 24px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  background: linear-gradient(135deg, #409eff 0%, #3a8ee6 100%) !important;
  border: none !important;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.submit-donation-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
  background: linear-gradient(135deg, #3a8ee6 0%, #3071a9 100%) !important;
}

.submit-donation-btn:active:not(:disabled) {
  transform: translateY(0);
}

/* 结果提示 */
.donation-result {
  margin-top: 30px;
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

.result-alert {
  border-radius: 12px !important;
  overflow: hidden;
}

.success-message {
  font-size: 16px;
  color: #27ae60;
  font-weight: 500;
  line-height: 1.6;
}

.donation-amount-highlight {
  font-size: 18px;
  font-weight: 700;
  color: #e74c3c;
}

.error-message {
  font-size: 15px;
  color: #e74c3c;
  line-height: 1.6;
}

.transaction-id {
  margin-top: 8px;
  font-size: 14px;
  color: #7f8c8d;
}

.transaction-id code {
  background: #f1f3f4;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
}

/* 暗黑模式 */
@media (prefers-color-scheme: dark) {
  .donation-page-wrapper {
    background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  }
  
  .donation-container {
    background-color: #2c3e50;
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
    border: 1px solid #34495e;
  }
  
  .card-shadow {
    background: #34495e;
    border-color: #455a64;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  }
  
  .donation-header h1 {
    color: #ecf0f1;
  }
  
  .subtitle {
    color: #bdc3c7;
  }
  
  .form-item-custom .el-form-item__label {
    color: #bdc3c7;
  }
  
  .amount-btn {
    background: #455a64 !important;
    border-color: #546e7a !important;
    color: #ecf0f1 !important;
  }
  
  .amount-btn:hover {
    border-color: #409eff !important;
    color: #409eff !important;
  }
  
  .currency-symbol {
    color: #bdc3c7;
  }
  
  .transaction-id code {
    background: #455a64;
    color: #ecf0f1;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .donation-page-wrapper {
    padding: 20px 15px;
  }
  
  .donation-container {
    padding: 25px 20px;
    border-radius: 12px;
  }
  
  .donation-header h1 {
    font-size: 24px;
  }
  
  .card-shadow {
    padding: 20px;
  }
  
  .amount-options {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .form-item-custom .el-form-item__label {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .amount-options {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .submit-donation-btn {
    max-width: 100%;
  }
  
  .donation-container {
    padding: 20px 15px;
  }
}
</style>