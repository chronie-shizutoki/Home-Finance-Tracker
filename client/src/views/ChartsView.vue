<template>
  <div class="container">
    <!-- 顶部加载和错误提示 -->
    <div v-if="isLoadingCsv" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- 页面标题 -->
    <Header :title="t('chart.title')" />

    <!-- 消费图表分析 -->
    <ExpenseCharts :expenses="csvExpenses" />

    <!-- 返回主页按钮 -->
    <div class="back-button-container">
      <el-button type="primary" @click="goBack" size="default">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('common.back') }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElButton, ElIcon } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import axios from 'axios';

import { useExpenseData } from '@/composables/useExpenseData';
import Header from '@/components/Header.vue';
import ExpenseCharts from '@/components/ExpenseCharts.vue';
import MessageTip from '@/components/MessageTip.vue';

const { t } = useI18n();
const router = useRouter();

// 状态数据
const csvExpenses = ref([]);
const isLoadingCsv = ref(false);

// 费用数据管理
const { error, successMessage, errorMessage } = useExpenseData();

// 载入消费数据（从SQLite数据库）
const loadCsvExpenses = async () => {
  if (isLoadingCsv.value) return;
  isLoadingCsv.value = true;

  try {
    // 使用与HomeView相同的API端点获取数据
    const res = await axios.get(`/api/expenses?limit=10000`);
    
    let parsedData = [];
    
    // 适配API响应格式
    if (res.data && res.data.data && Array.isArray(res.data.data)) {
      parsedData = res.data.data;
    } else if (Array.isArray(res.data)) {
      parsedData = res.data;
    }

    // 确保数据格式正确
    csvExpenses.value = parsedData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        time: item.time
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);

    if (csvExpenses.value.length === 0) {
      console.warn('ChartsView: No valid data found in API response');
    } else {
      console.log('ChartsView: Data loaded, count:', csvExpenses.value.length);
    }
  } catch (err) {
    const errorInfo = err.response
      ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
      : err.message;
    errorMessage.value = t('error.loadCsvFailed', { error: errorInfo });
    error.value = errorMessage.value;

    console.error('ChartsView: Error Details:', err);
    csvExpenses.value = [];
  } finally {
    isLoadingCsv.value = false;
  }
};

// 返回主页
const goBack = () => {
  router.push('/');
};

// 组件挂载时加载数据
onMounted(async () => {
  try {
    await loadCsvExpenses();
  } catch (err) {
    console.error('Failed to initialize data:', err);
    error.value = t('error.dataInitializationFailed');
  }
});
</script>

<style scoped>
.container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.loading-alert,
.error-alert {
  padding: 10px;
  margin-bottom: 15px;
  border-radius: 4px;
  text-align: center;
}

.loading-alert {
  background-color: #e3f2fd;
  color: #1976d2;
  border: 1px solid #90caf9;
}

.error-alert {
  background-color: #ffebee;
  color: #d32f2f;
  border: 1px solid #ffcdd2;
}

.back-button-container {
  margin-top: 30px;
  text-align: center;
}
</style>