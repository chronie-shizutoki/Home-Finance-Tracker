<template>
  <!-- Standalone dialog container -->
  <transition name="dialog-fade">
    <div v-if="modelValue" class="custom-dialog-overlay" @click.self="closeDialog">
        <div v-if="modelValue" class="custom-dialog settings-panel-dialog">
          <div class="dialog-header">
            <h3 class="dialog-title">{{ $t('spending.settings.title') }}</h3>
            <button class="dialog-close-btn" @click="closeDialog" aria-label="关闭">
              <FontAwesomeIcon icon="times" />
            </button>
          </div>
          
          <div class="dialog-body">
            <div class="spending-limit-setting">
              <MessageTip v-model:message="successMessage" type="success" />
              <MessageTip v-model:message="errorMessage" type="error" />
              <div class="setting-header">
                <h3 class="setting-title">{{ $t('spending.settings.on') }}</h3>
                <GlassSwitch
                  v-model="spendingStore.isLimitEnabled"
                  @change="handleToggleEnabled"
                  active-text=""
                  inactive-text=""
                  class="enable-switch"
                >
                  <template #active-text>{{ $t('spending.settings.enabled') }}</template>
                  <template #inactive-text>{{ $t('spending.settings.disabled') }}</template>
                </GlassSwitch>
              </div>

              <div class="setting-content" v-if="spendingStore.isLimitEnabled">
                <!-- Monthly limit setting -->
                <div class="setting-item">
                  <label class="setting-label">{{ $t('spending.settings.monthlyLimit') }}</label>
                  <GlassInputNumber
                    v-model="localLimit"
                    @change="handleLimitChange"
                    :min="0"
                    :max="999999"
                    :step="1000"
                    :precision="2"
                    :placeholder="$t('spending.settings.enterLimit')"
                    class="limit-input"
                    size="large"
                    :prefix="$t('common.currencySymbol')"
                  />
                </div>

                <!-- Warning threshold setting -->
                <div class="setting-item">
                  <label class="setting-label">{{ $t('spending.settings.warningThreshold') }}</label>
                  <GlassInputNumber
                    v-model="thresholdPercentage"
                    @change="handleThresholdChange"
                    :min="10"
                    :max="100"
                    :step="10"
                    :precision="0"
                    :placeholder="$t('spending.settings.enterThreshold')"
                    class="threshold-input"
                    size="large"
                    suffix="%"
                  />
                </div>
              </div>

              <!-- Disabled-state notice -->
              <div class="disabled-notice" v-else>
                <div class="notice-icon">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="16" x2="12" y2="12"></line>
                    <line x1="12" y1="8" x2="12.01" y2="8"></line>
                  </svg>
                </div>
                <span>{{ $t('spending.settings.disabledNotice') }}</span>
              </div>
            </div>
          </div>
        </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import { useSpendingStore } from '../stores/spending.js';
import { useI18n } from 'vue-i18n';
import GlassSwitch from './GlassSwitch.vue';
import GlassInputNumber from './GlassInputNumber.vue';
import MessageTip from './MessageTip.vue';

const { t } = useI18n();
const spendingStore = useSpendingStore();

// Props
const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
});

// Emits
const emit = defineEmits(['update:modelValue']);

// Local state
const localLimit = ref(0);
const thresholdPercentage = ref(80);
const successMessage = ref('');
const errorMessage = ref('');

// Methods
const handleToggleEnabled = (enabled) => {
  spendingStore.toggleLimitEnabled(enabled);
  if (enabled && spendingStore.monthlyLimit <= 0) {
    // If enabled but no limit is set, prompt the user to set one
    errorMessage.value = t('spending.settings.pleaseSetLimit');
  }
};

const handleLimitChange = (value) => {
  if (value !== null && value >= 0) {
    spendingStore.setMonthlyLimit(value);
  }
};

const handleThresholdChange = (value) => {
  if (value !== null && value >= 1 && value <= 100) {
    const threshold = value / 100;
    spendingStore.setWarningThreshold(threshold);
  }
};

// Watch the store and sync to local state
watch(() => spendingStore.monthlyLimit, (newValue) => {
  localLimit.value = newValue;
}, { immediate: true });

watch(() => spendingStore.warningThreshold, (newValue) => {
  thresholdPercentage.value = Math.round(newValue * 100);
}, { immediate: true });

// Close the dialog
const closeDialog = () => {
  emit('update:modelValue', false);
};

// Load settings when the component mounts
onMounted(() => {
  spendingStore.loadSettings();
});
</script>

<style scoped>
.custom-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  /* Blur the lower layer so the modal reads as a distinct surface */
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.custom-dialog {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: var(--border-radius-lg, 20px);
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.dialog-close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #909399;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
}

.dialog-close-btn:hover {
  color: #606266;
}

.dialog-body {
  padding: 24px;
}

.spending-limit-setting {
  background: transparent;
  padding: 0;
  box-shadow: none;
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
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.setting-content {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  flex-wrap: wrap;
}

.setting-item {
  margin-bottom: 24px;
  flex: 1;
}

.setting-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.limit-input {
  width: 100%;
  max-width: 300px;
}

.threshold-input {
  width: 100%;
  max-width: 300px;
}

.currency-symbol {
  color: #909399;
  font-weight: 500;
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



/* Dialog close-button animation */
.dialog-close-btn:hover {
  transform: rotate(90deg);
}

.dialog-close-btn {
  transition: transform 0.3s ease;
}

/* Responsive design */
@media (max-width: 768px) {
  .custom-dialog {
    width: 95%;
    margin: 10px;
  }
  
  .dialog-header,
  .dialog-body {
    padding: 16px;
  }
  
  .spending-limit-setting {
    padding: 0;
  }
}

/* Dark mode adaptation */
@media (prefers-color-scheme: dark) {
  .custom-dialog-overlay {
    background-color: rgba(0, 0, 0, 0.7);
  }
  
  .custom-dialog {
    background: rgba(30, 41, 59, 0.96);
    border: 1px solid rgba(255, 255, 255, 0.12);
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.35);
  }
  
  .dialog-header {
    border-bottom-color: #444;
  }
  
  .dialog-title {
    color: #f9fafb;
  }
  
  .dialog-close-btn {
    color: #9ca3af;
  }
  
  .dialog-close-btn:hover {
    color: #e5e7eb;
  }
  
  .spending-limit-setting {
    background: transparent;
    border: none;
    box-shadow: none;
  }

  .setting-header {
    border-bottom: 1px solid #444;
  }

  .setting-title {
    color: #f9fafb;
  }

  .setting-label {
    color: #9ca3af;
  }

  .currency-symbol {
    color: #9ca3af;
  }

  .negative-amount {
    color: #f87171;
  }
}


/* Custom dialog styles */
.custom-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  /* Blur the lower layer so the modal reads as a distinct surface */
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.custom-dialog {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: var(--border-radius-lg, 20px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

@media (prefers-color-scheme: dark) {
.custom-dialog {
  background: rgba(30, 41, 59, 0.96);
  color: #ffffff;
}
}

/* Unified modal open/close animation — matches the AI-feature GlassDialog style */
.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-fade-enter-active .custom-dialog,
.dialog-fade-leave-active .custom-dialog {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}

.dialog-fade-enter-from .custom-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(-10px);
}

.dialog-fade-leave-to .custom-dialog {
  opacity: 0;
  transform: scale(0.97) translateY(10px);
}

.dialog-fade-enter-to .custom-dialog,
.dialog-fade-leave-from .custom-dialog {
  opacity: 1;
  transform: scale(1) translateY(0);
}

.dialog-fade-enter-active .dialog-header,
.dialog-fade-enter-active .dialog-body,
.dialog-fade-enter-active .dialog-footer {
  animation: glassDialogContentIn 0.24s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  opacity: 0;
  transform: translateY(10px);
}

.dialog-fade-leave-active .dialog-header,
.dialog-fade-leave-active .dialog-body,
.dialog-fade-leave-active .dialog-footer {
  animation: glassDialogContentOut 0.24s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

@media (prefers-color-scheme: dark) {
.custom-dialog .dialog-header {
  border-bottom-color: #4a5568;
}
}

.dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a202c;
}

@media (prefers-color-scheme: dark) {
.custom-dialog .dialog-title {
  color: #f7fafc;
}
}

.dialog-close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #718096;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s ease;
}

.dialog-close-btn:hover {
  background-color: #f7fafc;
  color: #4a5568;
}

@media (prefers-color-scheme: dark) {
.custom-dialog .dialog-close-btn:hover {
  background-color: #4a5568;
  color: #e2e8f0;
}
}

.dialog-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.custom-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

</style>
