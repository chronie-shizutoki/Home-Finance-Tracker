<template>
  <div v-liquid-glass class="ai-report-filter">
    <div class="filter-section">
      <h4 class="filter-title">{{ t('ai.report.filterTitle') }}</h4>
      
      <!-- Year and month selection -->
      <div class="filter-row date-filter-row">
        <div class="date-select-wrapper">
          <span class="date-label">年份</span>
          <CustomSelect
            v-model="localYear"
            :options="availableYears"
            :empty-option-label="'全部年份'"
            :include-empty-option="true"
            @change="handleYearChange"
          />
        </div>
        
        <div class="date-select-wrapper">
          <span class="date-label">月份</span>
          <CustomSelect
            v-model="localMonth"
            :options="months"
            :empty-option-label="'全部月份'"
            :include-empty-option="true"
            @change="handleMonthChange"
          />
        </div>
      </div>
      
      <!-- Expense type multi-select -->
      <div class="filter-row types-section">
        <GlassFormItem label="消费类型（可多选）" class="filter-item types-label">
          <div class="type-checkboxes" :class="{ 'has-active': localSelectedTypes.length > 0 }">
            <label 
              v-for="type in expenseTypes" 
              :key="type" 
              class="checkbox-label"
              :class="{ active: localSelectedTypes.includes(type) }"
            >
              <input 
                type="checkbox" 
                :value="type" 
                v-model="localSelectedTypes"
                class="checkbox-input"
              />
              <span class="checkbox-custom"></span>
              <span class="checkbox-text">{{ type }}</span>
            </label>
          </div>
        </GlassFormItem>
      </div>
      
      <!-- Quick-select buttons -->
      <div class="quick-buttons">
        <GlassButton size="small" variant="secondary" @click="selectCurrentMonth">
          <span class="btn-icon">◉</span>本月
        </GlassButton>
        <GlassButton size="small" variant="secondary" @click="selectLastMonth">
          <span class="btn-icon">◑</span>上月
        </GlassButton>
        <GlassButton size="small" variant="secondary" @click="selectThisYear">
          <span class="btn-icon">⊙</span>本年
        </GlassButton>
        <GlassButton size="small" variant="outline" @click="clearFilters">
          <span class="btn-icon">✕</span>清空筛选
        </GlassButton>
      </div>
      
      <!-- Data statistics preview -->
      <div class="stats-preview">
        <div class="stats-header">
          <h5>{{ t('ai.report.statsPreview') }}</h5>
          <span class="stats-badge" v-if="filteredExpensesCount > 0">
            {{ filteredExpensesCount }} 条记录
          </span>
        </div>
        <div v-if="isLoading" class="loading-state">
          <div class="loading-spinner">
            <div class="spinner-ring"></div>
          </div>
          <span class="loading-text">{{ t('ai.report.loading') }}</span>
        </div>
        <div v-else class="stats-grid">
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.totalCount') }}</span>
            <span class="stat-value primary">{{ stats.totalCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.totalAmount') }}</span>
            <span class="stat-value success">¥{{ formatNumber(stats.totalAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.averageAmount') }}</span>
            <span class="stat-value">¥{{ formatNumber(stats.averageAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.medianAmount') }}</span>
            <span class="stat-value">¥{{ formatNumber(stats.medianAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.minAmount') }}</span>
            <span class="stat-value warning">¥{{ formatNumber(stats.minAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.maxAmount') }}</span>
            <span class="stat-value danger">¥{{ formatNumber(stats.maxAmount) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import GlassFormItem from '@/components/GlassFormItem.vue';
import GlassButton from '@/components/GlassButton.vue';
import CustomSelect from '@/components/CustomSelect.vue';
import { ExpenseAPI } from '@/api/expenses';

const { t } = useI18n();

const emit = defineEmits(['filterChange']);

// List of expense types
const expenseTypes = [
  '日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品', 
  '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他', 
  '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气', '美容美发', 
  '豆制品', '个护美妆', '电子产品', '家用电器', '五金', '服装'
];

// Month options
const months = [
  { value: '01', label: '1月' },
  { value: '02', label: '2月' },
  { value: '03', label: '3月' },
  { value: '04', label: '4月' },
  { value: '05', label: '5月' },
  { value: '06', label: '6月' },
  { value: '07', label: '7月' },
  { value: '08', label: '8月' },
  { value: '09', label: '9月' },
  { value: '10', label: '10月' },
  { value: '11', label: '11月' },
  { value: '12', label: '12月' }
];

// Current date
const now = new Date();
const currentYear = now.getFullYear();
const currentMonth = String(now.getMonth() + 1).padStart(2, '0');

// Available years (last 10 years)
const availableYears = computed(() => {
  return Array.from({ length: 10 }, (_, i) => ({
    value: String(currentYear - i),
    label: `${currentYear - i}年`
  }));
});

// Loading state
const isLoading = ref(true);
// All expense data
const allExpenses = ref([]);

// Local filter state
const localYear = ref('');
const localMonth = ref('');
const localSelectedTypes = ref([]);

// Fetch all expense data
const fetchAllExpenses = async () => {
  isLoading.value = true;
  try {
    console.log('AIReportFilter: Fetching all expenses...');
    
    // Fetch all data using pagination
    const allData = [];
    let page = 1;
    const limit = 100;
    let hasMore = true;
    
    while (hasMore) {
      const response = await ExpenseAPI.getExpenses(page, limit);
      const data = response?.data?.data || [];
      
      if (data.length > 0) {
        allData.push(...data);
        page++;
        // If fewer records are returned than requested, there is no more data
        if (data.length < limit) {
          hasMore = false;
        }
      } else {
        hasMore = false;
      }
    }
    
    // Ensure the data format is correct
    allExpenses.value = allData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        date: item.date
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);
    
    console.log('AIReportFilter: Fetched expenses count:', allExpenses.value.length);
    
    // Trigger a filter update (includes filter condition info)
    emit('filterChange', {
      ...stats.value,
      filterConditions: {
        year: localYear.value,
        month: localMonth.value,
        types: [...localSelectedTypes.value]
      }
    });
  } catch (error) {
    console.error('AIReportFilter: Error fetching expenses:', error);
    allExpenses.value = [];
  } finally {
    isLoading.value = false;
  }
};

// Filtered expense data
const filteredExpenses = computed(() => {
  let result = [...allExpenses.value];
  
  // Filter by year
  if (localYear.value) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return expenseDate.getFullYear() === parseInt(localYear.value);
      } catch {
        return false;
      }
    });
  }
  
  // Filter by month
  if (localMonth.value) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return String(expenseDate.getMonth() + 1).padStart(2, '0') === localMonth.value;
      } catch {
        return false;
      }
    });
  }
  
  // Filter by type
  if (localSelectedTypes.value.length > 0) {
    result = result.filter(expense => 
      localSelectedTypes.value.includes(expense.type)
    );
  }
  
  return result;
});

// Statistics data
const stats = computed(() => {
  const expenses = filteredExpenses.value;
  const amounts = expenses
    .map(e => parseFloat(e.amount))
    .filter(a => !isNaN(a))
    .sort((a, b) => a - b);
  
  const totalCount = expenses.length;
  const totalAmount = amounts.reduce((sum, a) => sum + a, 0);
  const averageAmount = totalCount > 0 ? totalAmount / totalCount : 0;
  const medianAmount = totalCount > 0 
    ? amounts[Math.floor(amounts.length / 2)] 
    : 0;
  const minAmount = amounts.length > 0 ? amounts[0] : 0;
  const maxAmount = amounts.length > 0 ? amounts[amounts.length - 1] : 0;
  
  return {
    totalCount,
    totalAmount,
    averageAmount,
    medianAmount,
    minAmount,
    maxAmount,
    filteredExpenses: expenses
  };
});

// Filtered expenses count for badge display
const filteredExpensesCount = computed(() => filteredExpenses.value.length);

// Format number with thousand separators
const formatNumber = (num) => {
  if (num === 0) return '0.00';
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

// Watch for filter changes and notify the parent component
watch([localYear, localMonth, localSelectedTypes], () => {
  console.log('Filter changed:', {
    year: localYear.value,
    month: localMonth.value,
    types: localSelectedTypes.value,
    filteredCount: filteredExpenses.value.length
  });
  
  // Pass the complete filter info and statistics data
  emit('filterChange', {
    ...stats.value,
    filterConditions: {
      year: localYear.value,
      month: localMonth.value,
      types: [...localSelectedTypes.value]
    }
  });
}, { deep: true });

// Handle year change
const handleYearChange = (value) => {
  console.log('Year changed:', value);
  localYear.value = value;
};

// Handle month change
const handleMonthChange = (value) => {
  console.log('Month changed:', value);
  localMonth.value = value;
};

// Quick-select methods
const selectCurrentMonth = () => {
  localYear.value = String(currentYear);
  localMonth.value = currentMonth;
};

const selectLastMonth = () => {
  const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  localYear.value = String(lastMonth.getFullYear());
  localMonth.value = String(lastMonth.getMonth() + 1).padStart(2, '0');
};

const selectThisYear = () => {
  localYear.value = String(currentYear);
  localMonth.value = '';
};

const clearFilters = () => {
  localYear.value = '';
  localMonth.value = '';
  localSelectedTypes.value = [];
};

// Fetch data on initialization
onMounted(() => {
  fetchAllExpenses();
});

// Expose methods for external calls
defineExpose({
  getStats: () => stats.value,
  clearFilters,
  refreshData: fetchAllExpenses
});
</script>

<style scoped src="../styles/components/AIReportFilter.css"></style>