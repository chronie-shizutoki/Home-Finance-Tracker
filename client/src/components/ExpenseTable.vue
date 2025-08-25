<!-- ExpenseTable.vue -->
<template>
  <div class="expense-container">
    <!-- 大屏幕表格视图 -->
    <div class="table-view">
      <table class="expense-table">
        <thead>
          <tr>
            <th @click="$emit('sort', 'time')" class="sortable">
              {{ $t('expense.date') }}
              <span v-if="sortField === 'time'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th @click="$emit('sort', 'type')" class="sortable">
              {{ $t('expense.type') }}
              <span v-if="sortField === 'type'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th @click="$emit('sort', 'amount')" class="sortable">
              {{ $t('expense.amount') }}
              <span v-if="sortField === 'amount'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th>{{ $t('expense.remark') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="expense in expenses" :key="expense.id">
            <td>{{ formatDate(expense.time) }}</td>
            <td>
              <span class="type-tag" :style="{ backgroundColor: getTypeColor(expense.type, isDarkMode) }">
                {{ expense.type }}
              </span>
            </td>
            <td class="amount-cell">¥{{ expense.amount.toFixed(2) }}</td>
            <td>{{ expense.remark || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 小屏幕卡片视图 -->
    <div class="card-view">
      <div v-for="expense in expenses" :key="expense.id" class="expense-card">
        <div class="card-header">
          <div class="date">{{ formatDate(expense.time) }}</div>
          <div class="amount">¥{{ expense.amount.toFixed(2) }}</div>
        </div>
        <div class="card-body">
          <div class="type-section">
            <span class="type-label">{{ $t('expense.type') }}:</span>
            <span class="type-tag" :style="{ backgroundColor: getTypeColor(expense.type, isDarkMode) }">
              {{ expense.type }}
            </span>
          </div>
          <div v-if="expense.remark" class="remark-section">
            <span class="remark-label">{{ $t('expense.remark') }}:</span>
            <span class="remark-text">{{ expense.remark }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空数据状态 -->
    <div v-if="expenses.length === 0" class="no-data">
      <div class="no-data-icon"></div>
      <h3>{{ $t('expense.noDataTitle') }}</h3>
      <p>{{ $t('expense.noDataMessage') }}</p>
    </div>
  </div>
</template>

<script>
import { getTypeColor } from '../utils/expenseUtils';
import { ref, onMounted, onUnmounted } from 'vue';

export default {
  props: {
    expenses: Array,
    sortField: String,
    sortOrder: String
  },

  setup () {
    const isDarkMode = ref(false);
    
    // 检测当前系统主题
    const checkDarkMode = () => {
      isDarkMode.value = window.matchMedia('(prefers-color-scheme: dark)').matches;
    };
    
    // 初始化检测
    checkDarkMode();
    
    // 监听主题变化
    onMounted(() => {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', checkDarkMode);
    });
    
    // 清理监听器
    onUnmounted(() => {
      window.matchMedia('(prefers-color-scheme: dark)').removeEventListener('change', checkDarkMode);
    });

    const formatDate = (dateString) => {
      if (!dateString) return '';
      const date = new Date(dateString);
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };

    return {
      getTypeColor,
      formatDate,
      isDarkMode
    };
  }
};
</script>

<style scoped>
.expense-container {
  background: transparent;
  border-radius: 10px;
  overflow: hidden;
}

/* 表格视图样式 */
.table-view {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.expense-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.expense-table th {
  background-color: #4361ee;
  color: white;
  text-align: left;
  padding: 12px 15px;
  font-weight: 600;
}

.expense-table td {
  padding: 10px 15px;
  border-bottom: 1px solid #e9ecef;
}

.expense-table tr:hover {
  background-color: rgba(67, 97, 238, 0.03);
}

/* 卡片视图样式 */
.card-view {
  display: none;
}

.expense-card {
  background: white;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.expense-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.date {
  font-size: 14px;
  color: #666;
}

.amount {
  font-size: 18px;
  font-weight: 600;
  color: #4361ee;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-label,
.remark-label {
  font-size: 12px;
  color: #999;
  min-width: 50px;
}

.remark-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.remark-text {
  font-size: 14px;
  color: #333;
  flex: 1;
  word-break: break-word;
}

/* 通用样式 */
.sortable {
  cursor: pointer;
  position: relative;
  user-select: none;
}

.sort-indicator {
  position: absolute;
  right: 5px;
}

.type-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: rgb(0, 0, 0);
}

.amount-cell {
  font-weight: 600;
  color: #2b2d42;
}

.no-data {
  text-align: center;
  padding: 40px 20px;
}

.no-data-icon {
  font-size: 48px;
  color: #e9ecef;
  margin-bottom: 15px;
}

.no-data h3 {
  font-size: 18px;
  margin-bottom: 10px;
  color: #6c757d;
}

.no-data p {
  color: #6c757d;
  max-width: 500px;
  margin: 0 auto;
}

/* 响应式设计 - 小屏幕使用卡片视图 */
@media (max-width: 768px) {
  .table-view {
    display: none;
  }
  
  .card-view {
    display: block;
  }
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .table-view {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }

  .expense-table th {
    background-color: #333;
    color: #e0e0e0;
  }

  .expense-table td {
    color: #e0e0e0;
    border-bottom: 1px solid #444;
  }

  .expense-table tr:hover {
    background-color: rgba(255, 255, 255, 0.05);
  }

  .amount-cell {
    color: #e0e0e0;
  }

  .type-tag {
    color: white;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  }

  .no-data-icon {
    color: #333;
  }

  .no-data h3,
  .no-data p {
    color: #aaa;
  }

  /* 深色模式下的卡片样式 */
  .expense-card {
    background-color: #2a2a2a;
    border: 1px solid #444;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }
  
  .card-header {
    border-bottom: 1px solid #444;
  }
  
  .date {
    color: #aaa;
  }
  
  .amount {
    color: #60a5fa;
  }
  
  .remark-text {
    color: #e0e0e0;
  }
  
  .type-label,
  .remark-label {
    color: #888;
  }
}
</style>
