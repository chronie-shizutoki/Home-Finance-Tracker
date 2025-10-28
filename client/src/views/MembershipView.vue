<template>
  <div class="membership-container">
    <h1 class="page-title">{{ $t('membership.title') }}</h1>
    
    <!-- 用户登录/注册表单 -->
    <div class="login-form" v-if="!isLoggedIn">
      <el-form :model="loginForm" @submit.prevent="handleLogin">
        <el-form-item label="用户名" :label-width="formLabelWidth">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" required></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin" :loading="isLoggingIn">
            {{ $t('membership.loginOrRegister') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <!-- 用户信息和当前会员状态 -->
    <div class="user-info-card" v-if="isLoggedIn && userInfo">
      <h2>{{ $t('membership.userInfo') }}</h2>
      <p>{{ $t('membership.username') }}: {{ userInfo.username }}</p>
      <p v-if="currentSubscription">
        {{ $t('membership.currentPlan') }}: {{ currentSubscription.SubscriptionPlan?.name }}
      </p>
      <p v-if="currentSubscription">
        {{ $t('membership.expiresOn') }}: {{ formatDate(currentSubscription.endDate) }}
      </p>
      <p v-if="!currentSubscription">
        {{ $t('membership.noActiveSubscription') }}
      </p>
      <!--
      取消订阅功能，此功能未完成，当前取消订阅不会为用户退款，当前不应该启用此功能
      <el-button 
        v-if="currentSubscription" 
        type="danger" 
        @click="cancelSubscription"
        :loading="isCancelling"
      >
        {{ $t('membership.cancelSubscription') }}
      </el-button> -->
      <el-button 
        type="warning" 
        @click="logout"
        style="margin-left: 10px"
      >
        {{ $t('membership.logout') }}
      </el-button>
    </div>
    
    <div style="text-align: center;">
    <p>当前付款方式仅限金流 (𝙲𝚑𝚛𝚢𝚜𝚘𝚛𝚛𝚑𝚘𝚎)，<a href="http://192.168.0.197:3100" target="_blank">点击这里</a>查看您的金流账户。</p>
    <br>
    </div>

    <!-- 订阅计划列表 -->
    <div class="plans-container">
      <h2>{{ $t('membership.selectPlan') }}</h2>
      <div class="plans-grid">
        <div 
          v-for="plan in subscriptionPlans" 
          :key="plan.id"
          class="plan-card"
          :class="{ selected: selectedPlanId === plan.id }"
          @click="selectPlan(plan.id)"
        >
          <div class="plan-header">
            <h3>{{ plan.name }}</h3>
            <div class="price-tag">¥{{ plan.price }}</div>
          </div>
          <div class="plan-description">{{ plan.description }}</div>
          <div class="plan-features">
            <div class="feature">{{ $t('membership.feature1') }}</div>
            <div class="feature">{{ $t('membership.feature2') }}</div>
            <div class="feature">{{ $t('membership.feature3') }}</div>
            <div class="feature">{{ $t('membership.feature4') }}</div>
          </div>
          <el-button 
            type="primary" 
            class="subscribe-button"
            :loading="isProcessing"
            @click.stop="subscribe(plan)"
          >
            {{ $t('membership.subscribe') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 金流服务费用提示 -->
    <div class="payment-fee-notice">
    <el-alert
      :title="$t('donation.feeNoticeTitle')"
        type="info"
      :closable="false"
        class="fee-alert"
      >
    <p class="fee-message">{{ $t('donation.feeNoticeContent') }}</p>
    </el-alert>
    <br>
  </div>
    
    <!-- 订阅历史记录 -->
    <div class="history-container" v-if="subscriptionHistory.length > 0">
      <h2>{{ $t('membership.subscriptionHistory') }}</h2>
      <el-table :data="subscriptionHistory" style="width: 100%">
        <el-table-column label="计划">
          <template #default="scope">
            {{ scope.row.SubscriptionPlan?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期">
          <template #default="scope">
            {{ formatDate(scope.row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="endDate" label="结束日期">
          <template #default="scope">
            {{ formatDate(scope.row.endDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag 
              :type="scope.row.status === 'active' ? 'success' : scope.row.status === 'expired' ? 'warning' : 'danger'"
            >
              {{ scope.row.status === 'cancelled' ? '已取消' : scope.row.status.charAt(0).toUpperCase() + scope.row.status.slice(1) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

export default {
  name: 'MembershipView',
  setup() {
    const subscriptionPlans = ref([])
    const selectedPlanId = ref(null)
    const isProcessing = ref(false)
    const isCancelling = ref(false)
    const isLoggingIn = ref(false)
    const userInfo = ref(null)
    const currentSubscription = ref(null)
    const subscriptionHistory = ref([])
    const isLoggedIn = ref(false)
    const loginForm = ref({
      username: ''
    })
    const formLabelWidth = '80px'
    
    // 从本地存储获取用户名
    const getUsername = () => {
      return localStorage.getItem('username') || ''
    }
    
    // 检查是否已登录
    const checkLoginStatus = () => {
      const username = getUsername()
      isLoggedIn.value = !!username
      loginForm.value.username = username
    }
    
    // 格式化日期
    const formatDate = (dateString) => {
      if (!dateString) return ''
      const date = new Date(dateString)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    }
    
    // 获取订阅计划列表
    const fetchSubscriptionPlans = async () => {
      try {
        // 使用正确的API路径格式
        const response = await fetch('/api/members/subscription-plans', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        if (response.ok) {
          const data = await response.json()
          subscriptionPlans.value = data.data || data
        } else {
          throw new Error('获取订阅计划失败')
        }
      } catch (error) {
        ElMessage.error('获取订阅计划失败')
        console.error('获取订阅计划失败:', error)
      }
    }
    
    // 获取用户信息和当前订阅
    const fetchUserInfo = async () => {
      if (!isLoggedIn.value) return
      
      try {
        const username = getUsername()
        
        // 优化：直接使用用户名调用API，避免404错误
        // 创建或获取用户 - 注意路径格式：/api/members/members/:username
        const userResponse = await fetch('/api/members/members/' + encodeURIComponent(username), {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        if (userResponse.ok) {
          const userData = await userResponse.json()
          userInfo.value = userData.data || userData
        } else {
          // 如果用户不存在，尝试创建用户
          const createUserResponse = await fetch('/api/members/members', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username })
          })
          
          if (createUserResponse.ok) {
            const userData = await createUserResponse.json()
            userInfo.value = userData.data || userData
          } else {
            throw new Error('无法创建用户')
          }
        }
        
        // 获取当前订阅
        try {
          const subscriptionResponse = await fetch('/api/members/members/' + encodeURIComponent(username) + '/current-subscription', {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json'
            }
          })
          
          if (subscriptionResponse.ok) {
            const subscriptionData = await subscriptionResponse.json()
            currentSubscription.value = subscriptionData.data || subscriptionData
          }
        } catch (subError) {
          console.error('获取当前订阅失败:', subError)
        }
        
        // 获取订阅历史
        try {
          const historyResponse = await fetch('/api/members/members/' + encodeURIComponent(username) + '/subscriptions', {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json'
            }
          })
          
          if (historyResponse.ok) {
            const historyData = await historyResponse.json()
            subscriptionHistory.value = historyData.data || historyData
          }
        } catch (histError) {
          console.error('获取订阅历史失败:', histError)
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        ElMessage.error('获取用户信息失败，请稍后重试')
      }
    }
    
    // 选择订阅计划
    const selectPlan = (planId) => {
      selectedPlanId.value = planId
    }
    
    // 处理登录/注册
    const handleLogin = async () => {
      if (!loginForm.value.username.trim()) {
        ElMessage.warning('请输入用户名')
        return
      }
      
      try {
        isLoggingIn.value = true
        
        // 保存用户名到本地存储
        localStorage.setItem('username', loginForm.value.username)
        isLoggedIn.value = true
        
        // 尝试创建或获取用户
        await fetchUserInfo()
        
        ElMessage.success('登录成功')
      } catch (error) {
        console.error('登录失败:', error)
        ElMessage.error('登录失败，请稍后重试')
      } finally {
        isLoggingIn.value = false
      }
    }
    
    // 处理登出
    const logout = () => {
      localStorage.removeItem('username')
      isLoggedIn.value = false
      userInfo.value = null
      currentSubscription.value = null
      subscriptionHistory.value = []
      ElMessage.success('已登出')
    }
    
    // 订阅处理
    const subscribe = async (plan) => {
      if (!isLoggedIn.value) {
        ElMessage.warning('请先登录')
        return
      }
      
      try {
        isProcessing.value = true
        const username = getUsername()
        
        // 调用订阅支付接口 - 直接使用fetch避免404错误
        const paymentResponse = await fetch('/api/payments/subscribe', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ username, planId: plan.period })
        })
        
        const paymentData = await paymentResponse.json()
        
        if (paymentResponse.ok && (paymentData.success || !paymentData.error)) {
          // 创建订阅记录 - 直接使用fetch避免404错误
          const createSubscriptionResponse = await fetch('/api/members/subscriptions', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              username,
              planId: plan.period,
              paymentId: paymentData.data?.orderId || 'temp-order-' + Date.now(),
              autoRenew: false
            })
          })
          
          const createData = await createSubscriptionResponse.json();
          if (createSubscriptionResponse.ok && (createData.success || !createData.error)) {
            ElMessage.success('订阅成功！')
            // 刷新用户信息和订阅状态
            await fetchUserInfo()
          } else {
            ElMessage.error('创建订阅记录失败')
          }
        } else {
          ElMessage.error(paymentData.error || '支付处理失败')
        }
      } catch (error) {
        ElMessage.error('订阅过程中发生错误')
        console.error('订阅失败:', error)
      } finally {
        isProcessing.value = false
      }
    }
    
    // 创建订阅记录
    const createSubscription = async (data) => {
      try {
        const response = await fetch('/api/members/subscriptions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify(data)
        })
        return await response.json()
      } catch (error) {
        throw error
      }
    }
    
    // 取消订阅
    const cancelSubscription = async () => {
      if (!isLoggedIn.value) {
        ElMessage.warning('请先登录')
        return
      }
      
      try {
        isCancelling.value = true
        const username = getUsername()
        
        // 直接使用fetch避免404错误
        const response = await fetch('/api/members/members/' + encodeURIComponent(username) + '/subscriptions', {
          method: 'DELETE',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        
        const responseData = await response.json()
        
        if (response.ok && (responseData.success || !responseData.error)) {
          ElMessage.success('订阅已取消')
          await fetchUserInfo()
        } else {
          ElMessage.error(responseData.error || '取消订阅失败')
        }
      } catch (error) {
        ElMessage.error('取消订阅过程中发生错误')
        console.error('取消订阅失败:', error)
      } finally {
        isCancelling.value = false
      }
    }
    
    // 页面加载时获取数据
    onMounted(() => {
      // 首先检查登录状态
      checkLoginStatus()
      // 获取订阅计划列表（无论是否登录都可以查看）
      fetchSubscriptionPlans()
      // 如果已登录，获取用户信息
      if (isLoggedIn.value) {
        fetchUserInfo()
      }
    })
    
    return {
      subscriptionPlans,
      selectedPlanId,
      isProcessing,
      isCancelling,
      isLoggingIn,
      userInfo,
      currentSubscription,
      subscriptionHistory,
      isLoggedIn,
      loginForm,
      formLabelWidth,
      selectPlan,
      subscribe,
      cancelSubscription,
      formatDate,
      handleLogin,
      logout
    }
  }
}
</script>

<style scoped>
.membership-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-title {
  text-align: center;
  margin-bottom: 30px;
  color: #333;
}

.user-info-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 30px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.login-form {
  max-width: 400px;
  margin: 0 auto 30px;
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.dark .login-form {
  background-color: #202020;
}

.plans-container {
  margin-bottom: 40px;
}

.plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.plan-card {
  background: #fff;
  border-radius: 8px;
  padding: 25px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid transparent;
}

.plan-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.plan-card.selected {
  border-color: #409eff;
}

.plan-header {
  text-align: center;
  margin-bottom: 15px;
}

.plan-header h3 {
  margin: 0 0 10px 0;
  color: #303133;
}

.price-tag {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.plan-description {
  text-align: center;
  color: #606266;
  margin-bottom: 20px;
  font-size: 14px;
}

.plan-features {
  margin-bottom: 20px;
}

.feature {
  padding: 8px 0;
  color: #606266;
  font-size: 14px;
  position: relative;
  padding-left: 20px;
}

.feature::before {
  content: '✓';
  color: #67c23a;
  position: absolute;
  left: 0;
}

.subscribe-button {
  width: 100%;
}

.history-container {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

@media (max-width: 768px) {
  .plans-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-color-scheme: dark) {
  .membership-container {
    background-color: #141414;
    color: #ffffff;
  }

  .user-info-card,
  .plan-card,
  .history-container,
  .login-form {
    background-color: #202020;
    border-color: #409eff;
  }

  .plan-header h3,
  .plan-description,
  .feature {
    color: #c0c4cc;
  }

  .price-tag {
    color: #409eff;
  }
  
  .el-form-item__label {
    color: #c0c4cc;
  }
  
  .el-input__wrapper {
    background-color: #303030;
  }
}
</style>