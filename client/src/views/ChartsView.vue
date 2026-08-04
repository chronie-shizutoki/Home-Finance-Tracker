<template>
  <div class="container">
    <!-- Loading and error banners -->
    <div v-if="isLoading" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- Page title -->
    <Header :title="t('chart.title')" />

    <!-- Back-to-home button -->
    <div class="back-button-container">
      <GlassButton type="primary" @click="goBack" size="default">
       <
      </GlassButton>
    </div>

    <!-- Expense chart analysis -->
    <ExpenseCharts :expenses="Expenses" />

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import GlassButton from '@/components/GlassButton.vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import axios from 'axios';

import { useExpenseData } from '@/composables/useExpenseData';
import { fetchAllPages, createCancellationController } from '@/utils/pagination';
import Header from '@/components/Header.vue';
import ExpenseCharts from '@/components/ExpenseCharts.vue';
import MessageTip from '@/components/MessageTip.vue';

const { t } = useI18n();
const router = useRouter();

// State data
const Expenses = ref([]);
const isLoading = ref(false);

// Cancellation controller
let paginationController = null;

// Expense data management
const { error, successMessage, errorMessage } = useExpenseData();

// Load expense data (from the SQLite database) - use paginated loading for performance
const loadExpenses = async () => {
  if (isLoading.value) return;

  // Cancel any previous request
  if (paginationController) {
    paginationController.abort();
  }

  paginationController = createCancellationController();
  isLoading.value = true;

  try {
    console.log('ChartsView: Starting pagination loading...');

    // Use the pagination utility to fetch all data
    const allData = await fetchAllPages({
      apiCall: ({ page, limit }) => 
        axios.get(`/api/expenses?page=${page}&limit=${limit}`),
      pageSize: 100,           // 100 records per page
      maxConcurrent: 2,        // Charts page uses 2 concurrent requests to avoid affecting other operations
      signal: paginationController.signal,
      onProgress: (progressData) => {
        console.log(`ChartsView: Pagination loading progress: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
      },
      onError: (error) => {
        console.error('ChartsView: Pagination loading error:', error);
        throw error;
      }
    });

    // Ensure the data format is correct
    Expenses.value = allData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        date: item.date
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);

    if (Expenses.value.length === 0) {
      console.warn('ChartsView: No valid data found in API response');
    } else {
      console.log('ChartsView: Data loading completed, count:', Expenses.value.length);
    }
  } catch (err) {
    if (err.message !== 'Canceled by user request') {
      const errorInfo = err.response
        ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
        : err.message;
      errorMessage.value = t('common.loadFailed', { error: errorInfo });
      error.value = errorMessage.value;

      console.error('ChartsView: Error Details:', err);
      Expenses.value = [];
    } else {
      console.log('ChartsView: Data loading canceled by user');
    }
  } finally {
    isLoading.value = false;
  }
};

// Return to home
const goBack = () => {
  router.push('/');
};

// Load data when the component is mounted
onMounted(async () => {
  try {
    await loadExpenses();
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

.error-alert {
  color: #d32f2f;
}
</style>