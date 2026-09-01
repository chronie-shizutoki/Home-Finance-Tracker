<template>
  <div class="spending-limit-display" v-if="spendingStore.isLimitEnabled" v-liquid-glass>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />
    <div class="display-header">
      <div class="header-left">
        <h4 class="display-title">{{ $t('spending.monthlyProgress') }}</h4>
        <span class="current-month">{{ currentMonthName }}</span>
      </div>
      <div class="header-right">
        <GlassButton
          @click="showSettings = true"
          size="small"
          type="text"
          class="settings-btn"
        >
          <template #icon><FontAwesomeIcon icon="cog" /></template>
          {{ $t('spending.settings.title') }}
        </GlassButton>
      </div>
    </div>

    <!-- Progress display -->
    <div class="progress-section">
      <div class="progress-info">
        <span class="current-spending">
          {{ $t('common.currencySymbol') }}{{ formatAmount(spendingStore.currentMonthSpending) }}
        </span>
        <span class="progress-separator">/</span>
        <span class="limit-amount">
          {{ $t('common.currencySymbol') }}{{ formatAmount(spendingStore.monthlyLimit) }}
        </span>
        <span class="percentage" :class="percentageClass">
          ({{ Math.round(spendingStore.spendingPercentage) }}%)
        </span>
      </div>

      <CustomProgress
        :percentage="Math.min(spendingStore.spendingPercentage, 100)"
        :color="progressColor"
        :stroke-width="12"
        class="spending-progress"
      />
    </div>

    <!-- Status display -->
    <div class="status-section">
      <GlassAlert
        :type="statusAlert.type"
        :closable="false"
        class="status-alert"
      >
        <strong>{{ statusAlert.title }}</strong><br>
        {{ statusAlert.description }}
      </GlassAlert>
    </div>

    <!-- Details display -->
    <div class="details-section">
      <div class="detail-item" v-if="spendingStore.isOverLimit">
        <span class="detail-label">{{ $t('spending.exceeded') }}:</span>
        <span class="detail-value exceeded-amount">
          {{ $t('common.currencySymbol') }}{{ formatAmount(spendingStore.currentMonthSpending - spendingStore.monthlyLimit) }}
        </span>
      </div>
      <div class="detail-item">
        <span class="detail-label">{{ $t('spending.dailyAverage') }}:</span>
        <span class="detail-value">
          {{ $t('common.currencySymbol') }}{{ formatAmount(dailyAverage) }}
        </span>
      </div>
      <div class="detail-item">
        <span class="detail-label">{{ $t('spending.recommendedDaily') }}:</span>
        <span class="detail-value" :class="recommendedDailyClass">
          {{ $t('common.currencySymbol') }}{{ formatAmount(recommendedDaily) }}
        </span>
      </div>
    </div>

    
  </div>

  <!-- Enable prompt -->
  <div class="enable-prompt" v-else>
    <div class="prompt-content">
<FontAwesomeIcon icon="chart-line" class="prompt-icon" />
      <div class="prompt-text">
        <h4>{{ $t('spending.enablePrompt.title') }}</h4>
        <p>{{ $t('spending.enablePrompt.description') }}</p>
      </div>
<GlassButton
        @click="handleEnableSpendingLimit"
        type="primary"
        class="enable-btn"
      >
        <template #icon><FontAwesomeIcon icon="plus" /></template>
        {{ $t('spending.enablePrompt.button') }}
      </GlassButton>
    </div>
  </div>
  
  <!-- Settings dialog -->
  <SpendingLimitSetting v-model="showSettings" />
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useSpendingStore } from '../stores/spending.js';
import { useI18n } from 'vue-i18n';
import { formatMonthLabelByLocale } from '../utils/dateFormatter.js';


import MessageTip from './MessageTip.vue';
import SpendingLimitSetting from './SpendingLimitSetting.vue';
import GlassAlert from './GlassAlert.vue';
import CustomProgress from './CustomProgress.vue';

const { t, locale } = useI18n();
const spendingStore = useSpendingStore();

// Local state
const successMessage = ref('');
const errorMessage = ref('');
// Control dialog display/hide
const showSettings = ref(false);

// Computed properties
const currentMonthName = computed(() => {
  return formatMonthLabelByLocale(new Date(), locale.value);
});

const percentageClass = computed(() => {
  const percentage = spendingStore.spendingPercentage;
  if (percentage >= 100) return 'over-limit';
  if (percentage >= spendingStore.warningThreshold * 100) return 'near-limit';
  return 'normal';
});

const progressColor = computed(() => {
  const percentage = spendingStore.spendingPercentage;
  if (percentage >= 100) return '#f56c6c';
  if (percentage >= spendingStore.warningThreshold * 100) return '#e6a23c';
  return '#67c23a';
});

const statusAlert = computed(() => {
  const status = spendingStore.getSpendingStatus();

  switch (status.type) {
  case 'danger':
    return {
      type: 'error',
      title: t('spending.alert.overLimit.title'),
      description: t('spending.alert.overLimit.description', {
        amount: t('common.currencySymbol') + formatAmount(status.data.overAmount)
      })
    };
  case 'warning':
    return {
      type: 'warning',
      title: t('spending.alert.nearLimit.title'),
      description: t('spending.alert.nearLimit.description', {
        remaining: t('common.currencySymbol') + formatAmount(status.data.remaining),
        percentage: status.data.percentage
      })
    };
  default:
    return {
      type: 'success',
      title: t('spending.alert.normal.title'),
      description: t('spending.alert.normal.description', {
        remaining: t('common.currencySymbol') + formatAmount(status.data.remaining),
        percentage: status.data.percentage
      })
    };
  }
});

// Calculate daily average spending
const dailyAverage = computed(() => {
  const now = new Date();
  const currentDay = now.getDate();
  return currentDay > 0 ? spendingStore.currentMonthSpending / currentDay : 0;
});

const recommendedDaily = computed(() => {
  const now = new Date();
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const currentDay = now.getDate();
  const remainingDays = daysInMonth - currentDay;

  if (remainingDays <= 0) return 0;
  return spendingStore.remainingAmount / remainingDays;
});

const recommendedDailyClass = computed(() => {
  const recommended = recommendedDaily.value;
  const average = dailyAverage.value;

  if (recommended <= 0) return 'negative-amount';
  if (recommended < average * 0.8) return 'warning-amount';
  return 'positive-amount';
});

// Methods
const formatAmount = (amount) => {
  return new Intl.NumberFormat('en-CA', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(amount || 0);
};

const enableSpendingLimit = () => {
  spendingStore.toggleLimitEnabled(true);
  successMessage.value = t('spending.enablePrompt.enabled');
};

const handleEnableSpendingLimit = () => {
  spendingStore.toggleLimitEnabled(true);
  showSettings.value = true;
  successMessage.value = t('spending.enablePrompt.enabled');
};

// Listen for expense data changes
const props = defineProps({
  expenses: {
    type: Array,
    default: () => []
  },
});

watch(() => props.expenses, (newExpenses) => {
  spendingStore.updateExpenses(newExpenses);
}, { immediate: true, deep: true });

// Component mounted
onMounted(() => {
  spendingStore.loadSettings();
  if (props.expenses && props.expenses.length > 0) {
    spendingStore.updateExpenses(props.expenses);
  }
});
</script>

<style scoped src="../styles/components/SpendingLimitDisplay.css"></style>
