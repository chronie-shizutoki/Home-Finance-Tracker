<!-- ExpenseStats.vue -->
<template>
    <div class="stats-container" v-liquid-glass>
      <div class="stats-summary">
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.rowCount') }}：</span>
          <span class="stat-value">{{ statistics.count }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.totalAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.totalAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.averageAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.averageAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.medianAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.medianAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.amountRange') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.minAmount }}-{{ $t('common.currencySymbol') }}{{ statistics.maxAmount }}</span>
        </div>
      </div>
    </div>
  </template>

<script>
import { onMounted, watch } from 'vue';

export default {
  props: {
    statistics: {
      type: Object,
      required: true
    }
  },
  setup(props) {
    // Component mounted log
    onMounted(() => {
      console.log('ExpenseStats component mounted:', {
        count: props.statistics.count,
        totalAmount: props.statistics.totalAmount
      });
    });
    
    // Listen for changes in statistics data
    watch(() => props.statistics, (newStats) => {
      console.log('Expense statistics updated:', {
        count: newStats.count,
        totalAmount: newStats.totalAmount,
        averageAmount: newStats.averageAmount
      });
    }, { deep: true });
    
    return {};
  }
};
</script>

  <style scoped src="../styles/components/ExpenseStats.css"></style>
