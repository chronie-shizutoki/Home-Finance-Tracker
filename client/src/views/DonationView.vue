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
            <div class="amount-selection-container">
              <el-select 
                v-model="donationForm.amount" 
                placeholder="选择金额" 
                class="amount-select"
                clearable
              >
                <el-option 
                  v-for="option in amountOptions" 
                  :key="option" 
                  :label="`${option} 元`" 
                  :value="option"
                />
                <el-option label="自定义金额" value="custom" />
              </el-select>
              
              <div v-if="donationForm.amount === 'custom' || donationForm.amount === ''" class="custom-amount-wrapper">
                <span class="currency-symbol">¥</span>
                <el-input 
                  v-model="customAmount" 
                  type="number" 
                  placeholder="请输入自定义金额"
                  class="custom-amount-input"
                  min="0" 
                  step="0.01"
                  @blur="applyCustomAmount"
                  @keyup.enter="applyCustomAmount"
                />
              </div>
              
              <div v-else class="selected-amount-display">
                <span class="selected-amount-label">已选择：</span>
                <span class="selected-amount-value">¥{{ donationForm.amount }}</span>
              </div>
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
      validator: (rule, value, callback) => {
        // Skip validation if value is 'custom' (user is entering custom amount)
        if (value === 'custom') {
          callback()
        } else if (!value || isNaN(parseFloat(value))) {
          callback(new Error('金额必须为数字'))
        } else {
          const numValue = parseFloat(value)
          if (numValue <= 0) {
            callback(new Error('金额必须为正数'))
          } else {
            // Validate maximum two decimal places
            const decimalPart = numValue.toString().split('.')[1]
            if (decimalPart && decimalPart.length > 2) {
              callback(new Error('金额最多保留两位小数'))
            } else {
              callback()
            }
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

// Custom amount input
const customAmount = ref('')

// Apply custom amount
const applyCustomAmount = () => {
  if (customAmount.value && parseFloat(customAmount.value) > 0) {
    donationForm.amount = parseFloat(customAmount.value)
  } else {
    donationForm.amount = ''
  }
}

// Handle donation
const handleDonate = async () => {
  if (!donationFormRef.value) return
  
  try {
    // Check if user selected custom amount but hasn't entered it yet
    if (donationForm.amount === 'custom' && (!customAmount.value || parseFloat(customAmount.value) <= 0)) {
      ElMessage.warning('请输入自定义金额')
      return
    }
    
    // Apply custom amount before validation if needed
    if (donationForm.amount === 'custom' && customAmount.value) {
      donationForm.amount = parseFloat(customAmount.value)
    }
    
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

/* 液态玻璃效果动画 */
@keyframes float {
  0% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
  100% { transform: translateY(0px); }
}

/* 页面容器 */
.donation-page-wrapper {
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-size: 400% 400%;
  animation: gradientBackground 15s ease infinite;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景装饰气泡 */
.donation-page-wrapper::before,
.donation-page-wrapper::after {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  z-index: 0;
}

.donation-page-wrapper::before {
  top: -100px;
  left: -100px;
  animation: float 8s ease-in-out infinite;
}

.donation-page-wrapper::after {
  bottom: -100px;
  right: -100px;
  animation: float 10s ease-in-out infinite reverse;
}

/* 主容器 - 液态玻璃效果 */
.donation-container {
  max-width: 600px;
  width: 100%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.18);
  animation: fadeIn 0.8s ease-out;
  position: relative;
  z-index: 1;
}

/* 头部样式 */
.donation-header {
  text-align: center;
  margin-bottom: 40px;
  position: relative;
  z-index: 2;
}

.header-icon {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
  animation: pulse 2s infinite;
}

/* SVG图标调整 */
.header-icon svg {
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.15));
}

.donation-header h1 {
  color: rgba(255, 255, 255, 0.95);
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: -0.5px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.subtitle {
  color: rgba(255, 255, 255, 0.85);
  font-size: 16px;
  line-height: 1.5;
  max-width: 400px;
  margin: 0 auto;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

/* 表单样式 */
.donation-form {
  width: 100%;
  position: relative;
  z-index: 2;
}

/* 液态玻璃卡片效果 */
.card-shadow {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: all 0.3s ease;
}

.card-shadow:hover {
  box-shadow: 0 12px 40px 0 rgba(31, 38, 135, 0.2);
  transform: translateY(-2px);
}

.form-item-custom {
  margin-bottom: 24px;
}

.form-item-custom .el-form-item__label {
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

/* 输入框液态玻璃效果 */
.custom-input {
  width: 100%;
  transition: all 0.3s ease;
}

:deep(.custom-input .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.2) !important;
  border: 1px solid rgba(255, 255, 255, 0.25) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-radius: 12px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:deep(.custom-input .el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.4) !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

:deep(.custom-input .el-input__wrapper:focus-within) {
  border-color: #409eff !important;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  transform: translateY(-1px);
}

:deep(.custom-input .el-input__inner) {
  color: rgba(255, 255, 255, 0.9) !important;
  background: transparent !important;
}

/* 金额选择容器 */
.amount-selection-container {
  width: 100%;
}

/* 金额选择下拉菜单 - 液态玻璃风格 */
.amount-select {
  width: 100%;
  margin-bottom: 16px;
}

:deep(.amount-select .el-select__wrapper) {
  background: rgba(255, 255, 255, 0.2) !important;
  border: 1px solid rgba(255, 255, 255, 0.25) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-radius: 12px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:deep(.amount-select .el-select__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.4) !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

:deep(.amount-select .el-select__wrapper:focus-within) {
  border-color: #409eff !important;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  transform: translateY(-1px);
}

:deep(.amount-select .el-select__placeholder) {
  color: rgba(255, 255, 255, 0.6) !important;
}

:deep(.amount-select .el-select__input) {
  color: rgba(255, 255, 255, 0.9) !important;
}

:deep(.amount-select .el-select__icon) {
  color: rgba(255, 255, 255, 0.7) !important;
}

/* 下拉菜单选项样式 */
:deep(.amount-select .el-select-dropdown) {
  background: rgba(255, 255, 255, 0.15) !important;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 12px !important;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

:deep(.amount-select .el-select-dropdown__item) {
  color: rgba(255, 255, 255, 0.9) !important;
  padding: 12px 16px;
  transition: all 0.3s ease;
}

:deep(.amount-select .el-select-dropdown__item:hover) {
  background: rgba(255, 255, 255, 0.2) !important;
  color: #409eff !important;
}

:deep(.amount-select .el-select-dropdown__item.selected) {
  background: rgba(64, 158, 255, 0.2) !important;
  color: #409eff !important;
}

/* 已选金额显示 */
.selected-amount-display {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: rgba(64, 158, 255, 0.15);
  border-radius: 12px;
  border: 1px solid rgba(64, 158, 255, 0.3);
  margin-top: 8px;
}

.selected-amount-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  margin-right: 8px;
}

.selected-amount-value {
  color: #409eff;
  font-size: 18px;
  font-weight: 700;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
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
  color: rgba(255, 255, 255, 0.8);
  z-index: 1;
}

/* 自定义金额输入框液态玻璃效果 */
:deep(.custom-amount-input .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.15) !important;
  border: 1px solid rgba(255, 255, 255, 0.25) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-radius: 12px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

:deep(.custom-amount-input .el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.4) !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

:deep(.custom-amount-input .el-input__wrapper:focus-within) {
  border-color: #409eff !important;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  transform: translateY(-1px);
}

:deep(.custom-amount-input .el-input__inner) {
  color: rgba(255, 255, 255, 0.9) !important;
  background: transparent !important;
  padding-left: 12px !important;
  font-size: 16px !important;
  height: 44px !important;
}

/* 提交按钮 */
.submit-button-container {
  display: flex;
  justify-content: center;
  margin-top: 32px !important;
}

/* 提交按钮液态玻璃风格 */
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
  position: relative;
  overflow: hidden;
}

/* 提交按钮发光效果 */
.submit-donation-btn::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(45deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transform: rotate(45deg);
  animation: shine 3s infinite;
}

@keyframes shine {
  0% { transform: translateX(-100%) rotate(45deg); }
  100% { transform: translateX(100%) rotate(45deg); }
}

.submit-donation-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
  background: linear-gradient(135deg, #3a8ee6 0%, #3071a9 100%) !important;
}

.submit-donation-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 结果提示 - 液态玻璃风格 */
.donation-result {
  margin-top: 30px;
  position: relative;
  z-index: 2;
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

/* 成功提示样式 */
:deep(.result-alert.el-alert--success) {
  background: rgba(39, 174, 96, 0.15) !important;
  border-color: rgba(39, 174, 96, 0.3) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 4px 12px rgba(39, 174, 96, 0.15);
}

/* 错误提示样式 */
:deep(.result-alert.el-alert--error) {
  background: rgba(231, 76, 60, 0.15) !important;
  border-color: rgba(231, 76, 60, 0.3) !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 4px 12px rgba(231, 76, 60, 0.15);
}

.success-message {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
  line-height: 1.6;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.donation-amount-highlight {
  font-size: 18px;
  font-weight: 700;
  color: #27ae60;
  text-shadow: 0 2px 4px rgba(39, 174, 96, 0.3);
}

.error-message {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.transaction-id {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.transaction-id code {
  background: rgba(255, 255, 255, 0.15);
  padding: 2px 8px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.result-alert {
  border-radius: 12px !important;
  overflow: hidden;
}

/* 暗黑模式 - 液态玻璃效果增强版 */
@media (prefers-color-scheme: dark) {
  .donation-page-wrapper {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }
  
  .donation-page-wrapper::before,
  .donation-page-wrapper::after {
    background: rgba(255, 255, 255, 0.05);
  }
  
  .donation-container {
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.3);
  }
  
  .card-shadow {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.2);
  }
  
  .donation-header h1 {
    color: rgba(255, 255, 255, 0.95);
  }
  
  .subtitle {
    color: rgba(255, 255, 255, 0.75);
  }
  
  .form-item-custom .el-form-item__label {
    color: rgba(255, 255, 255, 0.8);
  }
  
  /* 暗黑模式下的输入框 */
  :deep(.custom-input .el-input__wrapper),
  :deep(.custom-amount-input .el-input__wrapper) {
    background: rgba(255, 255, 255, 0.08) !important;
    border: 1px solid rgba(255, 255, 255, 0.15) !important;
  }
  
  :deep(.custom-input .el-input__wrapper:hover),
  :deep(.custom-amount-input .el-input__wrapper:hover) {
    border-color: rgba(255, 255, 255, 0.3) !important;
  }
  
  :deep(.custom-input .el-input__inner),
  :deep(.custom-amount-input .el-input__inner) {
    color: rgba(255, 255, 255, 0.9) !important;
  }
  
  /* 暗黑模式下的按钮 */
  .amount-btn {
    background: rgba(255, 255, 255, 0.08) !important;
    border-color: rgba(255, 255, 255, 0.15) !important;
    color: rgba(255, 255, 255, 0.8) !important;
  }
  
  .amount-btn:hover {
    background: rgba(255, 255, 255, 0.12) !important;
  }
  
  .currency-symbol {
    color: rgba(255, 255, 255, 0.7);
  }
  
  /* 暗黑模式下的结果提示 */
  .success-message,
  .error-message,
  .transaction-id {
    color: rgba(255, 255, 255, 0.85);
  }
  
  .transaction-id code {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.15);
    color: rgba(255, 255, 255, 0.9);
  }
  
  /* 暗黑模式下的提示框 */
  :deep(.result-alert.el-alert--success) {
    background: rgba(39, 174, 96, 0.1) !important;
    border-color: rgba(39, 174, 96, 0.2) !important;
  }
  
  :deep(.result-alert.el-alert--error) {
    background: rgba(231, 76, 60, 0.1) !important;
    border-color: rgba(231, 76, 60, 0.2) !important;
  }
}

/* 响应式设计优化 */
@media (max-width: 768px) {
  .donation-page-wrapper {
    padding: 20px 15px;
  }
  
  .donation-container {
    padding: 30px 20px;
    border-radius: 16px;
  }
  
  .donation-header h1 {
    font-size: 24px;
  }
  
  .card-shadow {
    padding: 25px;
  }
  
  .amount-options {
    grid-template-columns: repeat(3, 1fr);
    gap: 10px;
  }
  
  .form-item-custom .el-form-item__label {
    font-size: 13px;
  }
  
  /* 移动端背景装饰调整 */
  .donation-page-wrapper::before,
  .donation-page-wrapper::after {
    width: 200px;
    height: 200px;
  }
  
  .donation-page-wrapper::before {
    top: -50px;
    left: -50px;
  }
  
  .donation-page-wrapper::after {
    bottom: -50px;
    right: -50px;
  }
}

@media (max-width: 480px) {
  .submit-donation-btn {
    max-width: 100%;
  }
  
  .donation-container {
    padding: 25px 15px;
  }
  
  .card-shadow {
    padding: 20px;
  }
  
  /* 小屏幕选项菜单优化 */
  :deep(.amount-select .el-select__wrapper) {
    border-radius: 10px !important;
  }
  
  /* 小屏幕输入框优化 */
  :deep(.custom-input .el-input__wrapper),
  :deep(.custom-amount-input .el-input__wrapper) {
    border-radius: 10px !important;
  }
  
  /* 小屏幕下拉菜单优化 */
  :deep(.amount-select .el-select-dropdown) {
    border-radius: 10px !important;
    max-height: 200px;
  }
  
  :deep(.amount-select .el-select-dropdown__item) {
    padding: 10px 12px;
    font-size: 14px;
  }
  
  /* 小屏幕已选金额显示优化 */
  .selected-amount-display {
    padding: 10px 12px;
    margin-top: 6px;
  }
  
  .selected-amount-label {
    font-size: 13px;
  }
  
  .selected-amount-value {
    font-size: 16px;
  }
  
  /* 小屏幕图标调整 */
  .header-icon svg {
    width: 40px;
    height: 40px;
  }
}

/* 高分辨率屏幕优化 */
@media (-webkit-device-pixel-ratio: 2), (resolution: 192dpi) {
  .donation-container,
  .card-shadow,
  .custom-input .el-input__wrapper,
  .custom-amount-input .el-input__wrapper {
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .amount-btn:active {
    transform: scale(0.98);
  }
  
  .submit-donation-btn:active:not(:disabled) {
    transform: scale(0.98);
  }
}
</style>