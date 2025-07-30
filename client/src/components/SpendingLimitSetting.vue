<template>
  <div class="spending-limit-setting">
    <div class="setting-header">
      <h3 class="setting-title">{{ $t('spending.settings.title') }}</h3>
      <el-switch
        v-model="spendingStore.isLimitEnabled"
        @change="handleToggleEnabled"
        :active-text="$t('spending.settings.enabled')"
        :inactive-text="$t('spending.settings.disabled')"
        class="enable-switch"
      />
    </div>

    <div class="setting-content" v-if="spendingStore.isLimitEnabled">
      <!-- 月度限制设置 -->
      <div class="setting-item">
        <label class="setting-label">{{ $t('spending.settings.monthlyLimit') }}</label>
        <el-input-number
          v-model="localLimit"
          @change="handleLimitChange"
          :min="0"
          :max="999999"
          :step="100"
          :precision="2"
          :placeholder="$t('spending.settings.enterLimit')"
          class="limit-input"
          size="large"
        >
          <template #prefix>
            <span class="currency-symbol">¥</span>
          </template>
        </el-input-number>
      </div>

      <!-- 警告阈值设置 -->
      <div class="setting-item">
        <label class="setting-label">
          {{ $t('spending.settings.warningThreshold') }}
          <span class="threshold-value">({{ Math.round(spendingStore.warningThreshold * 100) }}%)</span>
        </label>
        <el-slider
          v-model="thresholdPercentage"
          @change="handleThresholdChange"
          :min="50"
          :max="95"
          :step="5"
          :format-tooltip="formatTooltip"
          show-stops
          class="threshold-slider"
        />
        <div class="threshold-description">
          {{ $t('spending.settings.thresholdDescription') }}
        </div>
      </div>

      <!-- 快速设置按钮 -->
      <div class="setting-item">
        <label class="setting-label">{{ $t('spending.settings.quickSet') }}</label>
        <div class="quick-buttons">
          <el-button
            v-for="amount in quickAmounts"
            :key="amount"
            @click="setQuickLimit(amount)"
            size="small"
            :type="localLimit === amount ? 'primary' : 'default'"
            class="quick-btn"
          >
            ¥{{ formatAmount(amount) }}
          </el-button>
        </div>
      </div>

      <!-- 当前状态预览 -->
      <div class="setting-item">
        <label class="setting-label">{{ $t('spending.settings.preview') }}</label>
        <div class="preview-card">
          <div class="preview-item">
            <span class="preview-label">{{ $t('spending.currentMonth') }}:</span>
            <span class="preview-value">{{ currentMonthName }}</span>
          </div>
          <div class="preview-item">
            <span class="preview-label">{{ $t('spending.currentSpending') }}:</span>
            <span class="preview-value spending-amount">¥{{ formatAmount(spendingStore.currentMonthSpending) }}</span>
          </div>
          <div class="preview-item">
            <span class="preview-label">{{ $t('spending.monthlyLimit') }}:</span>
            <span class="preview-value limit-amount">¥{{ formatAmount(spendingStore.monthlyLimit) }}</span>
          </div>
          <div class="preview-item">
            <span class="preview-label">{{ $t('spending.remaining') }}:</span>
            <span class="preview-value" :class="remainingClass">
              ¥{{ formatAmount(spendingStore.remainingAmount) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 禁用状态说明 -->
    <div class="disabled-notice" v-else>
      <el-icon class="notice-icon"><InfoFilled /></el-icon>
      <span>{{ $t('spending.settings.disabledNotice') }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useSpendingStore } from '../stores/spending.js';
import { useI18n } from 'vue-i18n';
import { InfoFilled } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';

const { t } = useI18n();
const spendingStore = useSpendingStore();

// 本地状态
const localLimit = ref(0);
const thresholdPercentage = ref(80);

// 快速设置金额选项
const quickAmounts = [1000, 2000, 3000, 5000, 8000, 10000];

// 计算属性
const currentMonthName = computed(() => {
  return dayjs().format('YYYY-MM');
});

const remainingClass = computed(() => {
  const remaining = spendingStore.remainingAmount;
  if (remaining <= 0) return 'negative-amount';
  if (remaining < spendingStore.monthlyLimit * 0.2) return 'warning-amount';
  return 'positive-amount';
});

// 方法
const handleToggleEnabled = (enabled) => {
  spendingStore.toggleLimitEnabled(enabled);
  if (enabled && spendingStore.monthlyLimit <= 0) {
    // 如果启用但没有设置限制，提示用户设置
    ElMessage.info(t('spending.settings.pleaseSetLimit'));
  }
};

const handleLimitChange = (value) => {
  if (value !== null && value >= 0) {
    spendingStore.setMonthlyLimit(value);
  }
};

const handleThresholdChange = (value) => {
  const threshold = value / 100;
  spendingStore.setWarningThreshold(threshold);
};

const setQuickLimit = (amount) => {
  localLimit.value = amount;
  spendingStore.setMonthlyLimit(amount);
  ElMessage.success(t('spending.settings.limitUpdated', { amount: formatAmount(amount) }));
};

const formatAmount = (amount) => {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(amount || 0);
};

const formatTooltip = (value) => {
  return `${value}%`;
};

// 监听store变化，同步到本地状态
watch(() => spendingStore.monthlyLimit, (newValue) => {
  localLimit.value = newValue;
}, { immediate: true });

watch(() => spendingStore.warningThreshold, (newValue) => {
  thresholdPercentage.value = Math.round(newValue * 100);
}, { immediate: true });

// 组件挂载时加载设置
onMounted(() => {
  spendingStore.loadSettings();
});
</script>

<style scoped>
.spending-limit-setting {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.setting-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.setting-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.enable-switch {
  --el-switch-on-color: #67c23a;
}

.setting-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.setting-item {
  margin-bottom: 24px;
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.threshold-value {
  color: #409eff;
  font-weight: 600;
}

.limit-input {
  width: 100%;
}

.currency-symbol {
  color: #909399;
  font-weight: 500;
}

.threshold-slider {
  margin: 16px 0;
}

.threshold-description {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.quick-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-btn {
  min-width: 80px;
}

.preview-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e4e7ed;
}

.preview-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.preview-item:last-child {
  margin-bottom: 0;
}

.preview-label {
  font-size: 13px;
  color: #606266;
}

.preview-value {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.spending-amount {
  color: #e6a23c;
}

.limit-amount {
  color: #409eff;
}

.positive-amount {
  color: #67c23a;
}

.warning-amount {
  color: #e6a23c;
}

.negative-amount {
  color: #f56c6c;
}

.disabled-notice {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #909399;
  font-size: 14px;
}

.notice-icon {
  margin-right: 8px;
  font-size: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .spending-limit-setting {
    padding: 16px;
  }

  .setting-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .quick-buttons {
    justify-content: center;
  }

  .quick-btn {
    flex: 1;
    min-width: 60px;
  }

  .preview-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
