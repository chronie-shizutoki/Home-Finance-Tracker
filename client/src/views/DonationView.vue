<template>
  <div class="container">
    <Header :title="t('donation.title')" />
    
    <div class="donation-content">
      <div class="donation-header">
        <div class="header-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="#4CAF50" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="#4CAF50" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="#4CAF50" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1>{{ t('donation.title') }}</h1>
        <p class="subtitle">{{ t('donation.description') }}</p>
        <p>当前付款方式仅限金流 (𝙲𝚑𝚛𝚢𝚜𝚘𝚛𝚛𝚑𝚘𝚎)，<a href="http://192.168.0.197:3100" target="_blank">点击这里</a>查看您的金流账户。</p>
      </div>
      
      <div class="donation-form card-shadow">
        <el-form :model="donationForm" :rules="donationRules" ref="donationFormRef" label-width="100px">
          <el-form-item :label="t('donation.username')" prop="username" class="form-item-custom">
            <el-input 
              v-model="donationForm.username" 
              :placeholder="t('donation.enterUsername')" 
              class="custom-input"
              prefix-icon="User"
            />
          </el-form-item>
        
          <el-form-item :label="t('donation.amount')" prop="amount" class="form-item-custom">
            <div class="amount-selection-container">
              <el-select 
                v-model="donationForm.amount" 
                :placeholder="t('donation.amount')" 
                class="amount-select"
                clearable
              >
                <el-option 
                  v-for="option in amountOptions" 
                  :key="option" 
                  :label="`${option} `" 
                  :value="option"
                />
                <el-option :label="t('donation.enterCustomAmount')" value="custom" />
              </el-select>
              
              <div v-if="donationForm.amount === 'custom' || donationForm.amount === ''" class="custom-amount-wrapper">
                <span class="currency-symbol">¥</span>
                <el-input 
                  v-model="customAmount" 
                  type="number" 
                  :placeholder="t('donation.enterCustomAmount')"
                  class="custom-amount-input"
                  min="0" 
                  step="0.01"
                  @blur="applyCustomAmount"
                  @keyup.enter="applyCustomAmount"
                />
              </div>
              
              <div v-else class="selected-amount-display">
                <span class="selected-amount-label">{{ t('donation.amount') }}：</span>
                <span class="selected-amount-value">¥{{ donationForm.amount }}</span>
              </div>
            </div>
          </el-form-item>
          
          <!-- 金流服务费用提示 -->
          <div class="payment-fee-notice">
            <el-alert
              :title="t('donation.feeNoticeTitle')"
              type="info"
              :closable="false"
              class="fee-alert"
            >
              <p class="fee-message">{{ t('donation.feeNoticeContent') }}</p>
            </el-alert>
          </div>
        
          <el-form-item class="submit-button-container">
            <el-button 
              type="primary" 
              @click="handleDonate" 
              :loading="isSubmitting"
              class="submit-donation-btn"
              :icon="isSubmitting ? 'Loading' : ''"
            >
              {{ isSubmitting ? '处理中...' : t('donation.submitDonation') }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
      
      <div v-if="donationResult" class="donation-result">
        <el-alert 
          :title="donationResult.success ? t('donation.successTitle') : t('donation.failureTitle')"
          :type="donationResult.success ? 'success' : 'error'"
          closable
          @close="resetDonationResult"
        >
          <template #default>
            <p v-if="donationResult.success" class="success-message">
              {{ t('donation.successMessage', { amount: donationForm.amount }) }}
            </p>
            <p v-else class="error-message">
              {{ donationResult.error }}
            </p>
            <p v-if="donationResult.data && donationResult.data.orderId" class="transaction-id">
              {{ t('donation.transactionId') }}: <code>{{ donationResult.data.orderId }}</code>
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
import { useI18n } from 'vue-i18n'
import Header from '@/components/Header.vue'

const { t } = useI18n()

// Define donation form data
const donationForm = reactive({
  username: '',
  amount: ''
})

// Define donation form validation rules
const donationRules = {
  username: [
    { required: true, message: t('donation.usernameRequired'), trigger: 'blur' }
  ],
  amount: [
    { required: true, message: t('donation.amountRequired'), trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        // Skip validation if value is 'custom' (user is entering custom amount)
        if (value === 'custom') {
          callback()
        } else if (!value || isNaN(parseFloat(value))) {
          callback(new Error(t('donation.amountMustBeNumber')))
        } else {
          const numValue = parseFloat(value)
          if (numValue < 0.01) {
            callback(new Error(t('donation.amountMustBeAtLeast')))
          } else {
            // Validate maximum two decimal places
            const decimalPart = numValue.toString().split('.')[1]
            if (decimalPart && decimalPart.length > 2) {
              callback(new Error(t('donation.amountMaxTwoDecimals')))
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
const amountOptions = [5, 10, 20, 40, 60, 80, 100]

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
    if (donationForm.amount === 'custom' && (!customAmount.value || parseFloat(customAmount.value) < 1)) {
      ElMessage.warning(t('donation.amountMustBeAtLeast100'))
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
      // 捐款成功，设置会话级别的完成标志（确保用户每次打开新会话都需要捐款）
      sessionStorage.setItem('hasCompletedCurrentSessionDonation', 'true');
      
      ElMessage.success(t('donation.donationSuccess'))
      
      // 显示成功消息后延迟跳转回首页
      setTimeout(() => {
        // 获取捐赠前的重定向URL，如果有的话
        const redirectUrl = localStorage.getItem('redirectAfterDonation');
        
        if (redirectUrl && redirectUrl !== '/donation') {
          // 清除重定向URL并跳转到该页面
          localStorage.removeItem('redirectAfterDonation');
          window.location.href = redirectUrl;
        } else {
          // 默认跳转到首页
          window.location.href = '/';
        }
      }, 2000);
    } else {
      ElMessage.error(t('donation.donationFailure'))
    }
  } catch (error) {
    console.error('捐赠失败:', error)
    ElMessage.error(t('donation.donationFailure'))
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
/* 定义 CSS 变量 */
:root {
  --text-primary: #333;
  --text-secondary: #666;
  --bg-primary: #fff;
  --border-primary: #e0e0e0;
  --primary-color: #4CAF50;
  --success-color: #67C23A;
  --danger-color: #F56C6C;
  --warning-color: #E6A23C;
  --info-color: #909399;
}

/* 主内容容器 */
.donation-content {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px 0;
}

/* 过渡动画 */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out;
}

/* 头部样式 */
.donation-header {
  text-align: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-primary);
}

.header-icon {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
}

.header-icon svg {
  width: 48px;
  height: 48px;
}

.donation-header h1 {
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 10px;
}

.subtitle {
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.5;
  max-width: 400px;
  margin: 0 auto;
}

/* 表单样式 */
.donation-form {
  width: 100%;
}

/* 卡片效果 */
.card-shadow {
  background-color: var(--bg-primary);
  border-radius: 8px;
  padding: 25px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid var(--border-primary);
}

.form-item-custom {
  margin-bottom: 24px;
}

.form-item-custom .el-form-item__label {
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
}

/* 金流服务费用提示样式 */
.payment-fee-notice {
  margin-bottom: 24px;
}

/* 费用提示框 */
:deep(.fee-alert.el-alert--info) {
  background-color: #f0f9ff;
  border-color: #d9ecff;
  border-radius: 6px !important;
}

:deep(.fee-alert .el-alert__title) {
  color: var(--info-color) !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  margin-bottom: 4px;
}

.fee-message {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin: 0;
}

/* 输入框样式 */
.custom-input {
  width: 100%;
}

/* 金额选择容器 */
.amount-selection-container {
  width: 100%;
}

/* 金额选择下拉菜单 */
.amount-select {
  width: 100%;
  margin-bottom: 16px;
}

/* 已选金额显示 */
.selected-amount-display {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background-color: #f0f9ff;
  border-radius: 6px;
  border: 1px solid #d9ecff;
  margin-top: 8px;
}

.selected-amount-label {
  color: var(--text-secondary);
  font-size: 14px;
  margin-right: 8px;
}

.selected-amount-value {
  color: var(--primary-color);
  font-size: 18px;
  font-weight: 700;
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
  color: var(--text-secondary);
  z-index: 1;
}

/* 自定义金额输入框 */
:deep(.custom-amount-input .el-input__inner) {
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

/* 提交按钮 */
.submit-donation-btn {
  width: 100%;
  max-width: 200px;
  height: 44px;
  border-radius: 6px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  background-color: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  transition: all 0.3s ease;
}

.submit-donation-btn:hover:not(:disabled) {
  background-color: #45a049 !important;
  border-color: #45a049 !important;
}

/* 结果提示 */
.donation-result {
  margin-top: 30px;
  animation: fadeIn 0.5s ease-out;
}

/* 成功提示样式 */
.success-message {
  font-size: 16px;
  color: var(--text-primary);
  font-weight: 500;
  line-height: 1.6;
}

.donation-amount-highlight {
  font-size: 18px;
  font-weight: 700;
  color: var(--success-color);
}

.error-message {
  font-size: 15px;
  color: var(--text-primary);
  line-height: 1.6;
}

.transaction-id {
  margin-top: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.transaction-id code {
  background-color: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
  color: var(--text-primary);
  border: 1px solid var(--border-primary);
}

.result-alert {
  border-radius: 6px !important;
}

/* 暗黑模式适配 */
@media (prefers-color-scheme: dark) {
  :root {
    --text-primary: #e5e7eb;
    --text-secondary: #9ca3af;
    --bg-primary: #1f2937;
    --border-primary: #374151;
  }
  
  .donation-header {
    border-bottom-color: var(--border-primary);
  }
  
  .card-shadow {
    background-color: var(--bg-primary);
    border-color: var(--border-primary);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }
  
  /* 暗黑模式下的提示框 */
  :deep(.fee-alert.el-alert--info) {
    background-color: rgba(144, 147, 153, 0.1);
    border-color: rgba(144, 147, 153, 0.2);
  }
  
  /* 暗黑模式下的成功提示框 */
  :deep(.donation-result .el-alert--success) {
    background-color: rgba(103, 194, 58, 0.1);
    border-color: rgba(103, 194, 58, 0.2);
  }
  
  /* 暗黑模式下的错误提示框 */
  :deep(.donation-result .el-alert--error) {
    background-color: rgba(245, 108, 108, 0.1);
    border-color: rgba(245, 108, 108, 0.2);
  }
  
  .selected-amount-display {
    background-color: rgba(64, 158, 255, 0.1);
    border-color: rgba(64, 158, 255, 0.2);
  }
  
  .transaction-id code {
    background-color: #2d3748;
    border-color: #4a5568;
    color: var(--text-primary);
  }
}

/* 响应式设计优化 */
@media (max-width: 768px) {
  .donation-content {
    padding: 15px 0;
  }
  
  .donation-header h1 {
    font-size: 22px;
  }
  
  .card-shadow {
    padding: 20px;
  }
}

@media (max-width: 480px) {
  .submit-donation-btn {
    max-width: 100%;
  }
  
  .card-shadow {
    padding: 16px;
  }
  
  .header-icon svg {
    width: 40px;
    height: 40px;
  }
  
  .donation-header h1 {
    font-size: 20px;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .submit-donation-btn:active:not(:disabled) {
    transform: scale(0.98);
  }
}
</style>