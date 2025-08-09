<!-- ExpenseStats.vue -->
<template>
    <div class="stats-container">
      <div class="stats-summary">
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.rowCount') }}：</span>
          <span class="stat-value">{{ statistics.count }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.totalAmount') }}：</span>
          <span class="stat-value">¥{{ statistics.totalAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.averageAmount') }}：</span>
          <span class="stat-value">¥{{ statistics.averageAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.medianAmount') }}：</span>
          <span class="stat-value">¥{{ statistics.medianAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.amountRange') }}：</span>
          <span class="stat-value">¥{{ statistics.minAmount }}-¥{{ statistics.maxAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.typeCount') }}：</span>
          <span class="stat-value">{{ statistics.uniqueTypeCount }}</span>
        </div>
      </div>
    </div>
  </template>

<script>
export default {
  props: {
    statistics: {
      type: Object,
      required: true
    }
  }
};
</script>

  <style scoped>
  .stats-container {
    background: transparent;
    border-radius: 10px;
    padding: 20px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

  .stats-summary {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 15px;
  }

  .stat-item {
    display: flex;
    justify-content: space-between;
    padding: 10px;
    background: transparent;
    border-radius: 8px;
}

  .stat-label {
    font-weight: 500;
    color: #495057;
  }

  .stat-value {
    font-weight: 600;
    color: #4361ee;
  }

  @media (prefers-color-scheme: dark) {
    .stats-container {
      background: rgba(30, 30, 30, 0.7);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    }

    .stat-item {
      background: rgba(40, 40, 40, 0.5);
    }

    .stat-label {
      color: #e9ecef;
    }

    .stat-value {
      color: #a5b4fc;
    }
  }
  </style>
