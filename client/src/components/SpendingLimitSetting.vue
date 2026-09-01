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
                <LiquidGlassSwitch
                  v-model="spendingStore.isLimitEnabled"
                  @update:modelValue="handleToggleEnabled"
                  size="xs"
                  class="enable-switch"
                />
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
import LiquidGlassSwitch from '../liquid-glass/LiquidGlassSwitch.vue';
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

<style scoped src="../styles/components/SpendingLimitSetting.css"></style>
