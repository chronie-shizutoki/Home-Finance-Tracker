<template>
  <div class="donation-container">
    <div class="donation-header">
      <h1>捐赠</h1>
      <p>请填写以下信息进行捐赠</p>
    </div>
    
    <div class="donation-form">
      <el-form :model="donationForm" :rules="donationRules" ref="donationFormRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="donationForm.username" placeholder="请输入用户名" />
        </el-form-item>
        
        <el-form-item label="金额" prop="amount">
          <div class="amount-options">
            <el-button 
              v-for="option in amountOptions" 
              :key="option" 
              type="primary" 
              :class="{ active: donationForm.amount === option }"
              @click="selectAmount(option)"
            >
              {{ option }}
            </el-button>
          </div>
          <el-input 
            v-model="donationForm.amount" 
            type="number" 
            placeholder="请输入自定义金额"
            class="custom-amount-input"
            min="0" 
            step="0.01"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleDonate" :loading="isSubmitting">
            提交捐赠
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div v-if="donationResult" class="donation-result">
      <el-alert 
        :title="donationResult.success ? '捐赠成功' : '捐赠失败'"
        :type="donationResult.success ? 'success' : 'error'"
        closable
        @close="resetDonationResult"
      >
        <template #default>
          <p v-if="donationResult.success">
            感谢您的捐赠，捐赠金额为 {{ donationForm.amount }}
          </p>
          <p v-else>
            {{ donationResult.error }}
          </p>
          <p v-if="donationResult.data && donationResult.data.transaction">
            交易ID: {{ donationResult.data.transaction.id }}
          </p>
        </template>
      </el-alert>
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
.donation-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.donation-header {
  text-align: center;
  margin-bottom: 30px;
}

.donation-header h1 {
  color: #303133;
  margin-bottom: 10px;
}

.donation-header p {
  color: #606266;
  font-size: 14px;
}

.donation-form {
  margin-bottom: 20px;
}

.amount-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 15px;
}

.amount-options .el-button {
  flex: 1;
  min-width: 80px;
}

.amount-options .el-button.active {
  background-color: #409eff;
  color: #fff;
}

.custom-amount-input {
  width: 100%;
}

.donation-result {
  margin-top: 20px;
}

@media (prefers-color-scheme: dark) {
  .donation-container {
    background-color: #1a1a1a;
    box-shadow: 0 2px 12px 0 rgba(255, 255, 255, 0.1);
  }
  
  .donation-header h1 {
    color: #e6e6e6;
  }
  
  .donation-header p {
    color: #cccccc;
  }
  
  .amount-options .el-button {
    background-color: #333;
    border-color: #4d4d4d;
    color: #e6e6e6;
  }
  
  .amount-options .el-button.active {
    background-color: #409eff;
    color: #fff;
  }
  
  .el-input {
    background-color: #333;
    border-color: #4d4d4d;
    color: #e6e6e6;
  }
}

@media (max-width: 768px) {
  .donation-container {
    padding: 15px;
  }
  
  .amount-options {
    flex-direction: column;
  }
  
  .amount-options .el-button {
    width: 100%;
  }
}
</style>