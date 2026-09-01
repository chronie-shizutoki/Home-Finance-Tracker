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
            @click="handleCardClick(expense, $event)"
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
            <!-- Inline action buttons (small screens) — shown when this card is the active one -->
            <div v-if="showMenu && currentMenuExpense && currentMenuExpense.id === expense.id" class="card-actions-inline">
              <button class="edit-btn" @click.stop="() => { handleEdit(expense); closeMenu() }">
                <FontAwesomeIcon icon="edit" />
                {{ $t('common.edit') }}
              </button>
              <button class="delete-btn" @click.stop="() => { handleDelete(expense.id); closeMenu() }">
                <FontAwesomeIcon icon="trash-alt" />
                {{ $t('common.delete') }}
              </button>
            </div>
          </div>
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
import { ref, onMounted, onUnmounted, watch, toRefs } from 'vue';
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

    // Action menu state
    const showMenu = ref(false);
    const menuExpenseId = ref('');
    const currentMenuExpense = ref(null);

    // Handle card click (small screens): click to toggle inline edit/delete buttons
    const handleCardClick = (expense, event) => {
      // If clicked card is already active, close the menu (toggle)
      if (currentMenuExpense.value && currentMenuExpense.value.id === expense.id && showMenu.value) {
        closeMenu();
        return;
      }
      // Open inline action buttons for the clicked card
      menuExpenseId.value = expense.id;
      currentMenuExpense.value = expense;
      showMenu.value = true;
      console.log('Card clicked, showing inline actions for expense:', expense.id);
    };

    // Close menu
    const closeMenu = () => {
      showMenu.value = false;
      menuExpenseId.value = '';
      currentMenuExpense.value = null;
    };

    // Click outside to close menu
    const handleClickOutside = (event) => {
      if (!showMenu.value) return;
      const card = event.target.closest('.expense-card');
      // If clicked outside any expense card, close
      if (!card) {
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
      handleCardClick,
      closeMenu,
      sortField,
      sortOrder
    };
  }
};
</script>

<style scoped src="../styles/components/ExpenseTable.css"></style>