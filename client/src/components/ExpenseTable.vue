<!-- ExpenseTable.vue -->
<template>
  <div class="expense-container" v-liquid-glass>
    <!-- Gradient definition SVG -->
    <svg class="gradient-defs" width="0" height="0">
      <defs>
        <linearGradient id="gradient-arrow" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#ff7eb3" stop-opacity="1" />
          <stop offset="100%" stop-color="#ff758c" stop-opacity="1" />
        </linearGradient>
      </defs>
    </svg>
    <!-- Large screen table view -->
    <div class="table-view">
      <table class="expense-table">
        <thead>
          <tr>
            <th @click="$emit('sort', 'date')" class="sortable">
              {{ $t('expense.date') }}
              <span v-if="sortField && sortField === 'date'" class="sort-indicator">
                <FontAwesomeIcon :icon="sortOrder === 'asc' ? 'arrow-up' : 'arrow-down'" />
              </span>
            </th>
            <th>
              {{ $t('expense.type') }}
            </th>
            <th @click="$emit('sort', 'amount')" class="sortable">
              {{ $t('expense.amount') }}
              <span v-if="sortField && sortField === 'amount'" class="sort-indicator">
                <FontAwesomeIcon :icon="sortOrder === 'asc' ? 'arrow-up' : 'arrow-down'" />
              </span>
            </th>
            <th>{{ $t('expense.remark') }}</th>
            <th>{{ $t('common.action') }}</th>
          </tr>
        </thead>
        <transition-group name="row-fade" tag="tbody">
          <template v-for="(expenses, date) in groupedExpenses" :key="date">
            <!-- Date header row -->
            <tr class="date-header-row">
              <td colspan="5">
                <div class="date-header">
                  <div class="date-info">
                    <span class="date-text">{{ formatDate(date) }}</span>
                    <span class="count-text">{{ $t('expense.stats.rowCount') }}: {{ expenses.length }}</span>
                  </div>
                  <div class="total-amount">-{{ $t('common.currencySymbol') }}{{ calculateDailyTotal(expenses).toFixed(2) }}</div>
                </div>
              </td>
            </tr>
            <!-- Expense items for the date row -->
            <tr v-for="(expense, index) in expenses" :key="expense.id" :data-index="index">
              <td>{{ formatExpenseDate(expense.date) }}</td>
              <td>
                <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type) }">
                  {{ expense.type }}
                </span>
              </td>
              <td class="amount-cell">{{ $t('common.currencySymbol') }}{{ expense.amount.toFixed(2) }}</td>
              <td class="remark-cell">{{ expense.remark || '-' }}</td>
              <td>
                <div class="action-buttons">
                  <button class="edit-btn" @click="handleEdit(expense)">
                    <FontAwesomeIcon icon="edit" /> 
                  </button>
                  <button class="delete-btn" @click="handleDelete(expense.id)">
                    <FontAwesomeIcon icon="trash-alt" /> 
                  </button>
                </div>
              </td>
            </tr>
          </template>
        </transition-group>
      </table>
    </div>

    <!-- Small screen card view -->
    <div class="card-view">
      <transition-group name="row-fade" tag="div">
        <template v-for="(expenses, date) in groupedExpenses" :key="date">
          <!-- Date header card -->
          <div class="date-header-card">
            <div class="date-info">
              <span class="date-text">{{ formatDate(date) }}</span>
              <span class="count-text">{{ $t('expense.stats.rowCount') }}: {{ expenses.length }}</span>
            </div>
            <div class="total-amount">-{{ $t('common.currencySymbol') }}{{ calculateDailyTotal(expenses).toFixed(2) }}</div>
          </div>
          <!-- Expense items for the date row -->
          <div 
            v-for="(expense, index) in expenses" 
            :key="expense.id" 
            class="expense-card" 
            :data-index="index"
            @touchstart="startLongPress(expense, $event)"
            @touchend="endLongPress"
            @touchcancel="endLongPress"
            @mousedown="startLongPress(expense, $event)"
            @mouseup="endLongPress"
            @mouseleave="endLongPress"
          >
            <div class="card-header">
              <div class="date">{{ formatExpenseDate(expense.date) }}</div>
              <div class="amount">{{ $t('common.currencySymbol') }}{{ expense.amount.toFixed(2) }}</div>
            </div>
            <div class="card-body">
              <div class="type-section">
                <span class="type-label">{{ $t('expense.type') }}:</span>
                <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type) }">
                    {{ expense.type }}
                </span>
              </div>
              <div v-if="expense.remark" class="remark-section">
                <span class="remark-label">{{ $t('expense.remark') }}:</span>
                <span class="remark-text">{{ expense.remark }}</span>
              </div>
            </div>
          </div>
        
        <!-- Long press menu -->
        <transition 
          name="menu-fade"
          mode="out-in"
        >
          <div 
            v-if="showMenu && currentMenuExpense" 
            key="menu"
            class="long-press-menu"
            :style="menuStyle"
          >
            <div class="menu-content">
              <button 
                class="menu-btn menu-edit-btn" 
                @click.stop="() => { handleEdit(currentMenuExpense); closeMenu() }"
              >
                <FontAwesomeIcon icon="edit" />
                {{ $t('common.edit') }}
              </button>
              <button 
                class="menu-btn menu-delete-btn" 
                @click.stop="() => { handleDelete(currentMenuExpense.id); closeMenu() }"
              >
                <FontAwesomeIcon icon="trash-alt" />
                {{ $t('common.delete') }}
              </button>
            </div>
          </div>
        </transition>
        </template>
      </transition-group>
    </div>

    <!-- No data status -->
    <div v-if="Object.keys(groupedExpenses).length === 0" class="no-data">
      <div class="no-data-icon"></div>
      <h3>{{ $t('expense.noDataTitle') }}</h3>
      <p>{{ $t('expense.noDataMessage') }}</p>
    </div>
  </div>
</template>

<script>
import { getTypeColor } from '../utils/expenseUtils';
import { formatRelativeDate } from '../utils/date-utils';
import { formatDateByLocale } from '../utils/dateFormatter';
import { ref, computed, onMounted, onUnmounted, watch, toRefs } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';
import { useI18n } from 'vue-i18n';

export default {
  components: {
    FontAwesomeIcon
  },
  props: {
    groupedExpenses: {
      type: Object,
      default: () => ({})
    },
    // Let sortField and sortOrder be optional properties
    sortField: {
      type: String,
      default: ''
    },
    sortOrder: {
      type: String,
      default: 'asc'
    }
  },

  setup (props, { emit }) {
    // Use toRefs to keep props reactive
    const { sortField, sortOrder } = toRefs(props);
    const { t, locale } = useI18n();
    
    const formatDate = (dateString) => {
      const result = formatRelativeDate(dateString, t);
      return result;
    };

    const formatExpenseDate = (dateString) => {
      return formatDateByLocale(dateString, locale.value);
    };

    // Calculate daily total
    const calculateDailyTotal = (expenses) => {
      return expenses.reduce((total, expense) => total + parseFloat(expense.amount || 0), 0);
    };

    // Long press related state
    const showMenu = ref(false);
    const menuExpenseId = ref('');
    const currentMenuExpense = ref(null);
    const menuPosition = ref({ x: 0, y: 0 });
    const longPressTimer = ref(null);
    const LONG_PRESS_DURATION = 500; // Long press trigger duration

    // Menu style calculation
    const menuStyle = computed(() => {
      return {
        top: `${menuPosition.value.y}px`,
        right: `${menuPosition.value.x}px`
      };
    });

    // Start long press
    const startLongPress = (expense, event) => {
      // Clear previous timer
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
      }
      
      // Save target reference to avoid loss in async callbacks
      const target = event.currentTarget;
      
      // Set new timer
      longPressTimer.value = setTimeout(() => {
        // Calculate menu position
        // Add null check to ensure target exists
        if (!target) {
          console.warn('Long press target is null or undefined');
          return;
        }
        
        const rect = target.getBoundingClientRect();
        // Get coordinates of touch or mouse event
        const clientX = event.touches ? event.touches[0].clientX : event.clientX;
        const clientY = event.touches ? event.touches[0].clientY : event.clientY;
        
        // Calculate menu position, near the long press position
        menuPosition.value = {
          x: window.innerWidth - clientX - 110, // Adjust menu width
          y: clientY + 10
        };
        
        // Set menu data first, then show menu
        menuExpenseId.value = expense.id;
        currentMenuExpense.value = expense;
        
        // Ensure DOM update before showing menu, trigger animation
        setTimeout(() => {
          showMenu.value = true;
          
          console.log('Long press detected, showing menu for expense:', expense.id);
          console.log('Menu state:', { showMenu: showMenu.value, menuExpenseId: menuExpenseId.value, menuPosition: menuPosition.value, currentMenuExpense: currentMenuExpense.value });
        }, 10);
      }, LONG_PRESS_DURATION);
    };

    // End long press
    const endLongPress = () => {
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
        longPressTimer.value = null;
      }
    };

    // Close menu
    const closeMenu = () => {
      showMenu.value = false;
      menuExpenseId.value = '';
      currentMenuExpense.value = null;
    };

    // Click outside to close menu
    const handleClickOutside = (event) => {
      if (showMenu.value && !event.target.closest('.long-press-menu')) {
        closeMenu();
      }
    };

    // Handle edit expense click
    const handleEdit = (expense) => {
      console.log('Edit expense clicked:', expense);
      emit('edit', expense);
    };

    // Handle delete expense click
    const handleDelete = (id) => {
      console.log('Delete expense clicked:', { id });
      emit('delete', id);
    };

    // Add global click event listener
    onMounted(() => {
      document.addEventListener('click', handleClickOutside);
    });

    // Remove global click event listener
    onUnmounted(() => {
      document.removeEventListener('click', handleClickOutside);
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
      }
    });

    // Listen for data changes
    watch(() => props.groupedExpenses, (newVal) => {
      const totalExpenses = Object.values(newVal || {}).reduce((sum, expenses) => sum + expenses.length, 0);
      console.log('Expense data updated:', { recordCount: totalExpenses });
    }, { deep: true });

    return {
      getTypeColor,
      formatDate,
      formatExpenseDate,
      calculateDailyTotal,
      handleEdit,
      handleDelete,
      showMenu,
      menuExpenseId,
      currentMenuExpense,
      menuStyle,
      startLongPress,
      endLongPress,
      closeMenu,
      sortField,
      sortOrder
    };
  }
};
</script>

<style scoped>
.expense-container {
  background: transparent;
  border-radius: var(--border-radius-lg);
  overflow: hidden;
}

/* Table view styles */
.table-view {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.expense-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.expense-table thead tr {
  background: rgba(255, 255, 255, 0.35);
  border-bottom: 1px solid rgba(255, 255, 255, 0.25);
}

.expense-table th {
  background: linear-gradient(90deg, #ff7eb3, #ff758c);
  -webkit-background-clip: text;
  -moz-background-clip: text;
  -ms-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  -moz-text-fill-color: transparent;
  -ms-text-fill-color: transparent;
  text-align: left;
  padding: 14px 18px;
  font-weight: 600;
  font-size: 15px;
}

.expense-table td {
  padding: 10px 15px;
  border-bottom: 1px solid #e9ecef;
}

.expense-table tr:hover {
  background-color: rgba(67, 97, 238, 0.03);
}

/* Date header row styles */
.date-header-row {
  background: rgba(255, 255, 255, 0.25);
}

.date-header-row td {
  padding: 10px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.date-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.date-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.date-text {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.count-text {
  font-size: 12px;
  color: #6c757d;
}

.total-amount {
  font-size: 16px;
  font-weight: 600;
  color: #e63946;
}

/* Card view styles */
.card-view {
  display: none;
}

/* Date header card styles */
.date-header-card {
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: var(--border-radius);
  padding: 12px 16px;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.date-header-card .date-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.date-header-card .date-text {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.date-header-card .count-text {
  font-size: 12px;
  color: #6c757d;
}

.date-header-card .total-amount {
  font-size: 18px;
  font-weight: 600;
  color: #e63946;
}

.expense-card {
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: var(--border-radius);
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
    white-space: pre-wrap;
    line-height: 1.5;
  }

  .remark-cell {
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.5;
    min-height: 40px;
  }

  .card-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 10px;
  }

  .card-edit-btn,
  .card-delete-btn {
    padding: 4px 12px;
    font-size: 12px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .card-edit-btn {
    background-color: #4361ee;
    color: white;
  }

  .card-edit-btn:hover {
    background-color: #3a56d4;
  }

  .card-delete-btn {
    background-color: #e63946;
    color: white;
  }

  .card-delete-btn:hover {
    background-color: #c1121f;
  }

/* Long press menu styles */
.long-press-menu {
  position: fixed;
  z-index: 9999;
  pointer-events: auto;
}

.menu-content {
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
  padding: 8px;
  min-width: 120px;
  width: 120px;
  overflow: hidden;
  z-index: 10000;
  transform-origin: top right;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.menu-btn {
  width: 100%;
  margin-bottom: 8px;
  font-size: 12px;
  padding: 6px 12px;
  transition: all 0.2s ease;
  font-weight: 500;
}

.menu-btn:last-child {
  margin-bottom: 0;
}

.menu-edit-btn {
  background-color: #4361ee;
  color: white;
}

.menu-edit-btn:hover {
  background-color: #3651c4;
}

.menu-delete-btn {
  background-color: #e63946;
  color: white;
}

.menu-delete-btn:hover {
  background-color: #c1121f;
}

@media (prefers-color-scheme: dark) {
  .menu-content {
    background: rgba(30, 41, 59, 0.96);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  }
}

/* Menu animation effects */
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: all 0.3s ease-out !important;
  transform-origin: top right !important;
  will-change: transform, opacity !important;
}

.menu-fade-enter-from {
  opacity: 0 !important;
  transform: scale(0.8) rotate(-10deg) !important;
}

.menu-fade-leave-to {
  opacity: 0 !important;
  transform: scale(0.8) rotate(10deg) !important;
}

/* Vue.js 2 animation class names */
.menu-fade-enter {
  opacity: 0 !important;
  transform: scale(0.8) rotate(-10deg) !important;
}

.menu-fade-leave-active {
  opacity: 1 !important;
  transform: scale(1) rotate(0deg) !important;
}

.menu-fade-leave-to {
  opacity: 0 !important;
  transform: scale(0.8) rotate(10deg) !important;
}

/* Dark mode adaptation */
@media (prefers-color-scheme: dark) {
  .menu-content {
    background-color: #2a2a2a;
    border: 1px solid #444;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  }
  
  .menu-btn {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }
  
  .menu-btn:hover {
    opacity: 0.9;
  }
}

/* Common styles */
.sortable {
  cursor: pointer;
  position: relative;
  user-select: none;
}

.sort-indicator {
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  margin-left: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

:deep(.sort-indicator svg) {
  width: 14px;
  height: 14px;
}

:deep(.sort-indicator svg path) {
  fill: url(#gradient-arrow);
}

/* Gradient definitions */
.gradient-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

.type-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: rgb(0, 0, 0);
  background-color: var(--tag-color);
  border: none;
}

.amount-cell {
  font-weight: 600;
  color: #2b2d42;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.edit-btn,
.delete-btn {
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s ease;
  background: rgba(67, 97, 238, 0.85);
  color: white;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.edit-btn:hover {
  background: rgba(67, 97, 238, 1);
  transform: translateY(-1px);
}

.delete-btn {
  background: rgba(230, 57, 70, 0.85);
}

.delete-btn:hover {
  background: rgba(230, 57, 70, 1);
  transform: translateY(-1px);
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

/* Table fade animation effects */
.row-fade-enter-active,
.row-fade-leave-active {
  transition: opacity 0.3s ease;
}

.row-fade-enter-from,
.row-fade-leave-to {
  opacity: 0;
}

/* Responsive design for small screens (use card view) */
@media (max-width: 768px) {
  .table-view {
    display: none;
  }
  
  .card-view {
    display: block;
  }
}

/* Dark mode support */
@media (prefers-color-scheme: dark) {
  .table-view {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }

  .expense-table thead tr {
    background: rgba(255, 255, 255, 0.08);
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }

  .expense-table td {
    color: #e0e0e0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  }

  .expense-table tr:hover {
    background-color: rgba(255, 255, 255, 0.05);
  }

  .amount-cell {
    color: #e0e0e0;
  }

  .remark-cell {
    color: #e0e0e0;
  }

  .type-tag {
    color: var(--tag-color);
    background-color: transparent;
    border: 1px solid white;
    box-shadow: none;
  }

  .no-data-icon {
    color: #333;
  }

  .no-data h3,
  .no-data p {
    color: #aaa;
  }

  /* Dark mode card styles */
  .expense-card {
    background: rgba(30, 30, 30, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .card-header {
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
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

  .card-edit-btn,
  .card-delete-btn {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .card-edit-btn:hover,
  .card-delete-btn:hover {
    opacity: 0.9;
  }

  /* Dark mode date header styles */
  .date-header-row {
    background: rgba(255, 255, 255, 0.06);
  }

  .date-header-row td {
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  }

  .date-text {
    color: #e0e0e0;
  }

  .count-text {
    color: #aaa;
  }

  .total-amount {
    color: #f87171;
  }

  .date-header-card {
    background: rgba(30, 30, 30, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .date-header-card .date-text {
    color: #e0e0e0;
  }

  .date-header-card .count-text {
    color: #aaa;
  }

  .date-header-card .total-amount {
    color: #f87171;
  }
}
</style>