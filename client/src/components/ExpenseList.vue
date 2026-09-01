<!-- ExpenseList.vue -->
<template>
  <div class="expense-list">
      <!-- Search component for expense list -->
      <ExpenseSearch
        ref="searchComponent"
        :uniqueTypes="uniqueTypes"
        :availableMonths="availableMonths"
        :initialKeyword="searchParams.keyword"
        :initialType="searchParams.type"
        :initialMonth="searchParams.month"
        :initialMinAmount="searchParams.minAmount"
        :initialMaxAmount="searchParams.maxAmount"
        :initialSortOption="searchParams.sortOption"
        :locale="$i18n.locale"
        @search="handleSearch"
      />

      <!-- Empty state prompt -->
      <div v-if="filteredExpenses.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path d="M19,13H5V11H19V13M12,5A2,2 0 0,1 14,7A2,2 0 0,1 12,9A2,2 0 0,1 10,7A2,2 0 0,1 12,5Z" />
          </svg>
        </div>
        <h3>{{ $t('expense.empty.title') }}</h3>
        <p>{{ $t('expense.empty.description') }}</p>
        <button @click="resetFilters" class="reset-button">
          {{ $t('expense.empty.reset') }}
        </button>
      </div>

      <template v-else>
        <!-- Statistics component for expense list -->
        <ExpenseStats :statistics="statistics" />

        <!-- Table component for expense list -->
        <ExpenseTable
          :groupedExpenses="groupedExpenses"
          :sortField="sortField"
          :sortOrder="sortOrder"
          @sort="sortBy"
          @edit="handleEdit"
          @delete="handleDelete"
        />

        <!-- Pagination component for expense list -->
        <ExpensePagination
          v-if="totalPages > 1"
          :currentPage="currentPage"
          :totalPages="totalPages"
          :visiblePages="visiblePages"
          @page-change="changePage"
        />
      </template>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch, watchEffect, onUnmounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter, useRoute } from 'vue-router';
import ExpenseStats from './ExpenseStats.vue';
import ExpenseSearch from './ExpenseSearch.vue';
import ExpenseTable from './ExpenseTable.vue';
import ExpensePagination from './ExpensePagination.vue';
import { getTypeColor } from '../utils/expenseUtils';
import { ExpenseAPI } from '../api/expenses';

export default {
  components: {
    ExpenseStats,
    ExpenseSearch,
    ExpenseTable,
    ExpensePagination
  },
  props: {
    // Signal to trigger data refresh on the client
    refreshTrigger: {
      type: Number,
      default: 0
    }
  },

  emits: ['refreshCompleted', 'edit', 'delete', 'data-loaded'],
  
  setup (props, { emit }) {
    const { t, locale } = useI18n();
    const router = useRouter();
    const route = useRoute();
    const searchComponent = ref(null);
    
    // Handle edit event
    const handleEdit = (expense) => {
      emit('edit', expense);
    };
    
    // Handle delete event
    const handleDelete = (id) => {
      emit('delete', id);
    };

    // Unified search parameters
    const searchParams = ref({
      keyword: '',
      type: '',
      month: '',
      minAmount: null,
      maxAmount: null,
      sortOption: 'dateDesc'
    });

    // Pagination related state
    const currentPage = ref(1);
    const pageSize = ref(10);
    const totalItems = ref(0);
    const expenses = ref([]);
    
    // Unique expense types
    const uniqueTypes = ref([]);
    // Default months for dropdown menu，used to ensure dropdown menu has options even before API request completes
    const defaultMonths = [];
    const now = new Date();
    // Generate last 120 months of data for dropdown menu
    for (let i = 0; i < 120; i++) {
      const year = now.getFullYear();
      const month = now.getMonth() - i;
      const date = new Date(year, month);
      defaultMonths.push(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`);
    }
    const availableMonths = ref(defaultMonths);

    // Listen for language change and regenerate month options when language changes
    watch(locale, (newLocale) => {
      console.log('Language changed, regenerating month options:', newLocale);
      // Fetch new data to update month display
      fetchPaginatedData();
    });
    
    // Remove frontend filtering logic and use backend data directly
    const filteredExpenses = computed(() => {
      // Check if groupedExpenses has data
      const dateGroups = Object.keys(groupedExpenses.value);
      if (dateGroups.length === 0) {
        return [];
      }
      // Return all expense items for each date group
      return dateGroups.flatMap(date => groupedExpenses.value[date]);
    });

    // Grouped expenses by date (using backend data)
    const groupedExpenses = ref({});
    
    // Fetch paginated data (using grouped by date API)
    const fetchPaginatedData = async () => {
      try {
        // Add check if searchParams.value exists
        if (!searchParams.value) {
          return;
        }
        
        console.log('Fetching paginated expenses by date:', {
          page: currentPage.value,
          pageSize: pageSize.value,
          searchParams: { ...searchParams.value }
        });
        
        // Add sort parameters to request URL
        const params = new URLSearchParams();
        params.append('page', currentPage.value);
        params.append('limit', pageSize.value);
        
        // Add search parameters
        if (searchParams.value.keyword) params.append('keyword', searchParams.value.keyword);
        if (searchParams.value.type) params.append('type', searchParams.value.type);
        if (searchParams.value.month) params.append('month', searchParams.value.month);
        // Validate amount parameters are valid numbers before adding
        const minAmount = parseFloat(searchParams.value.minAmount);
        const maxAmount = parseFloat(searchParams.value.maxAmount);
        if (searchParams.value.minAmount !== null && !isNaN(minAmount)) {
          params.append('minAmount', minAmount.toString());
        }
        if (searchParams.value.maxAmount !== null && !isNaN(maxAmount)) {
          params.append('maxAmount', maxAmount.toString());
        }
        if (searchParams.value.sortOption) params.append('sort', searchParams.value.sortOption);
        
        // Call grouped by date API
        const response = await ExpenseAPI.getExpensesByDate(currentPage.value, pageSize.value, params);
        
        // Handle response data format
        if (response && response.data && response.data.data && Array.isArray(response.data.data)) {
          // Backend response data format: [{ date: '2024-01-01', count: 5, totalAmount: 100, expenses: [...] }]
          // Backend uses intelligent pagination algorithm to ensure records for the same date group are not split across different pages
          // Each page will have records close to 10, but may exceed 10 to keep date group integrity
          // Backend will return total number of records in the response
          const dateGroups = response.data.data;
          
          // Convert backend response data to frontend format required
          const groups = {};
          dateGroups.forEach(group => {
            groups[group.date] = group.expenses;
          });
          
          groupedExpenses.value = groups;
          totalItems.value = response.data.total || 0; // Backend will return total number of records in the response
          
          // Update uniqueTypes and availableMonths from backend response
          if (response.data.meta) {
            uniqueTypes.value = response.data.meta.uniqueTypes || [];
            // Only update availableMonths if backend returns valid data
            if (response.data.meta.availableMonths && response.data.meta.availableMonths.length > 0) {
              // Sort months from newest to oldest
              // This is to match the default dropdown menu order
              availableMonths.value = response.data.meta.availableMonths.sort((a, b) => b.localeCompare(a));
            }
          }
        } else if (Array.isArray(response)) {
          // Compatibility for old format response
          const expenseList = response;
          const groups = {};
          expenseList.forEach(expense => {
            const date = expense.date;
            if (!groups[date]) {
              groups[date] = [];
            }
            groups[date].push(expense);
          });
          groupedExpenses.value = groups;
          totalItems.value = Object.keys(groups).length;
        }
        console.log('Expenses data fetched successfully:', {
          dateGroupCount: Object.keys(groupedExpenses.value).length,
          totalItems: totalItems.value,
          page: currentPage.value
        });
        // Notify parent component data has been loaded, pass total record count
        emit('data-loaded', { total: totalItems.value });
      } catch (error) {
        console.error('Error fetching paginated expenses data:', error);
        console.error('Data fetch error details:', { message: error.message, stack: error.stack });
      }
    };

    // Search processing
    const handleSearch = (params) => {
      console.log('Search requested with params:', { ...params });
      searchParams.value = { ...params };
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics(); // Update statistics data
    };

    // Reset filters processing
    const resetFilters = () => {
      console.log('Filters reset requested');
      if (searchComponent.value) {
        searchComponent.value.handleReset();
      }
      searchParams.value = {
        keyword: '',
        type: '',
        month: '',
        minAmount: null,
        maxAmount: null,
        sortOption: 'dateDesc'
      };
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics(); // Update statistics data
      console.log('Filters reset completed');
    };

    // Pagination processing
    const paginatedExpenses = computed(() => {
      return expenses.value || [];
    });

    // Total pages count
    const totalPages = computed(() => {
      return Math.ceil(totalItems.value / pageSize.value) || 1;
    });

    // Visible pages count（small screen max 1, large screen max 7）
    const visiblePages = computed(() => {
      const pages = [];
      const total = totalPages.value;
      const current = currentPage.value;
      // Determine max visible pages based on screen width
      const maxVisible = window.innerWidth < 768 ? 1 : 7;

      if (total <= maxVisible) {
        for (let i = 1; i <= total; i++) pages.push(i);
      } else {
        const start = Math.max(1, current - Math.floor(maxVisible / 2));
        const end = Math.min(total, start + maxVisible - 1);

        if (start > 1) pages.push(1);
        if (start > 2) pages.push('...');

        for (let i = start; i <= end; i++) pages.push(i);

        if (end < total - 1) pages.push('...');
        if (end < total) pages.push(total);
      }

      return pages;
    });

    // Statistics data
    const statistics = ref({
      count: 0,
      totalAmount: '0.00',
      averageAmount: '0.00',
      medianAmount: '0.00',
      minAmount: '0.00',
      maxAmount: '0.00',
      typeDistribution: {}
    });

    // Fetch statistics data
    const fetchStatistics = async () => {
      try {
        // Check if searchParams.value exists
        if (!searchParams.value) {
          return;
        }
        
        console.log('Fetching statistics with filters:', { ...searchParams.value });
        
        // Build query parameters
        const statsSearchParams = new URLSearchParams();
        if (searchParams.value.keyword) statsSearchParams.set('keyword', searchParams.value.keyword);
        if (searchParams.value.type) statsSearchParams.set('type', searchParams.value.type);
        if (searchParams.value.month) statsSearchParams.set('month', searchParams.value.month);
        
        // Add amount range parameters
        const validMinAmount = parseFloat(searchParams.value.minAmount);
        const validMaxAmount = parseFloat(searchParams.value.maxAmount);
        if (!isNaN(validMinAmount)) {
          statsSearchParams.set('minAmount', validMinAmount.toString());
        }
        if (!isNaN(validMaxAmount)) {
          statsSearchParams.set('maxAmount', validMaxAmount.toString());
        }

        // Call backend statistics API to get full data
        const statsData = await ExpenseAPI.getStatistics(statsSearchParams);
        
        // Format numbers to keep consistency
        if (statsData && !statsData.error) {
          statistics.value = {
            count: statsData.count || 0,
            totalAmount: (statsData.totalAmount || 0).toFixed(2),
            averageAmount: (statsData.averageAmount || 0).toFixed(2),
            medianAmount: (statsData.medianAmount || 0).toFixed(2),
            minAmount: (statsData.minAmount || 0).toFixed(2),
            maxAmount: (statsData.maxAmount || 0).toFixed(2),
            typeDistribution: statsData.typeDistribution || {}
          };
        }
          
          console.log('Statistics fetched successfully:', {
            recordCount: statistics.value.count,
            totalAmount: statistics.value.totalAmount,
            typeCategories: Object.keys(statistics.value.typeDistribution).length
          });
        
      } catch (error) {
        console.error('Statistics fetch failed:', error);
        console.error('Statistics fetch error details:', { message: error.message, stack: error.stack });
        // Keep existing statistics data on error
      }
    };

    // When filters change, fetch statistics again
    watchEffect(() => {
      // Check if searchParams.value exists
      if (!searchParams.value) {
        return;
      }
      
      // Delay execution to avoid frequent requests to backend
      const timer = setTimeout(() => {
        fetchStatistics();
      }, 300);
      
      return () => clearTimeout(timer);
    });

    // Initial fetch statistics on page load
    fetchStatistics();

    // Page change method
    const changePage = (page) => {
      console.log('Page change requested:', { from: currentPage.value, to: page, totalPages: totalPages.value });
      if (page >= 1 && page <= totalPages.value) {
        currentPage.value = page;
        fetchPaginatedData();
      }
    };

    // Sort method
    const sortBy = (field) => {
      const currentSort = searchParams.value.sortOption;
      let newSort = '';

      if (field === 'date') {
        newSort = currentSort === 'dateAsc' ? 'dateDesc' : 'dateAsc';
      } else if (field === 'amount') {
        newSort = currentSort === 'amountAsc' ? 'amountDesc' : 'amountAsc';
      }

      if (newSort) {
        console.log('Sort requested:', { field, from: currentSort, to: newSort });
        searchParams.value.sortOption = newSort;
        currentPage.value = 1;
        fetchPaginatedData();
      }
    };

    // Listen for state changes and update URL (using debounce)
    let updateURLTimer = null;
    watch(
      [currentPage, searchParams],
      () => {
        if (updateURLTimer) clearTimeout(updateURLTimer);
        updateURLTimer = setTimeout(() => {
          updateURL();
        }, 200);
      },
      { deep: true }
    );

    // Calculate sortField and sortOrder
    const sortField = computed(() => {
      const sortOption = searchParams.value.sortOption;
      console.log('Calculating sortField from:', sortOption);
      if (sortOption.startsWith('date')) {
        return 'date';
      } else if (sortOption.startsWith('amount')) {
        return 'amount';
      }
      return '';
    });

    const sortOrder = computed(() => {
      const sortOption = searchParams.value.sortOption;
      console.log('Calculating sortOrder from:', sortOption);
      if (sortOption.endsWith('Asc')) {
        return 'asc';
      } else if (sortOption.endsWith('Desc')) {
        return 'desc';
      }
      return 'asc';
    });

    // Listen for pageSize changes
    watch(pageSize, () => {
      currentPage.value = 1;
      fetchPaginatedData();
    });

    // When external refresh trigger is received, reload data and statistics
    watch(
      () => props.refreshTrigger,
      (newValue, oldValue) => {
        if (newValue !== oldValue) {
          console.log('Refresh triggered from parent, reloading data');
          // Reset to first page to show latest records
          currentPage.value = 1;
          fetchPaginatedData();
          fetchStatistics();
          // Notify parent component that refresh is completed
          emit('refreshCompleted');
        }
      }
    );

    // Provide manual refresh method for parent component to call
    const refreshData = () => {
      console.log('Manual data refresh requested');
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics();
    };

    // Mark if initializing from URL to prevent infinite loop
    let isInitializingFromURL = false;
    // Route listener reference to unwatch on component destruction
    let routeUnwatch = null;

    // Initialize state from URL query parameters
    const initializeFromURL = () => {
      isInitializingFromURL = true;
      
      // Read pagination parameter
      if (route.query.page) {
        const page = parseInt(route.query.page);
        if (!isNaN(page) && page > 0) {
          currentPage.value = page;
        }
      }

      // Read filter parameters
      searchParams.value = {
        keyword: route.query.keyword || '',
        type: route.query.type || '',
        month: route.query.month || '',
        minAmount: route.query.minAmount ? parseFloat(route.query.minAmount) : null,
        maxAmount: route.query.maxAmount ? parseFloat(route.query.maxAmount) : null,
        sortOption: route.query.sort || 'dateDesc'
      };

      isInitializingFromURL = false;
    };

    // Update URL query parameters (using native History API to avoid triggering Vue re-rendering)
    const updateURL = () => {
      if (isInitializingFromURL) return;

      const query = {};

      // Add pagination parameter
      if (currentPage.value !== 1) {
        query.page = currentPage.value.toString();
      }

      // Add filter parameters
      if (searchParams.value.keyword) query.keyword = searchParams.value.keyword;
      if (searchParams.value.type) query.type = searchParams.value.type;
      if (searchParams.value.month) query.month = searchParams.value.month;
      if (searchParams.value.minAmount !== null && !isNaN(searchParams.value.minAmount)) {
        query.minAmount = searchParams.value.minAmount.toString();
      }
      if (searchParams.value.maxAmount !== null && !isNaN(searchParams.value.maxAmount)) {
        query.maxAmount = searchParams.value.maxAmount.toString();
      }
      if (searchParams.value.sortOption && searchParams.value.sortOption !== 'dateDesc') {
        query.sort = searchParams.value.sortOption;
      }

      // Update URL query parameters (using native History API to avoid triggering Vue re-rendering)
      const url = new URL(window.location.href);
      
      // Clear old query parameters
      url.searchParams.delete('page');
      url.searchParams.delete('keyword');
      url.searchParams.delete('type');
      url.searchParams.delete('month');
      url.searchParams.delete('minAmount');
      url.searchParams.delete('maxAmount');
      url.searchParams.delete('sort');

      // Add new query parameters
      Object.entries(query).forEach(([key, value]) => {
        url.searchParams.set(key, value);
      });

      // Replace URL query parameters without refreshing the page or triggering Vue re-rendering
      window.history.replaceState({}, '', url.toString());
    };

    // Initialize state from URL query parameters
    onMounted(() => {
      console.log('ExpenseList component mounted, initializing from URL');
      initializeFromURL();
      fetchPaginatedData();
    });

    onUnmounted(() => {
      if (routeUnwatch) {
        routeUnwatch();
      }
      if (updateURLTimer) clearTimeout(updateURLTimer);
    });

    return {
      searchComponent,
      searchParams,
      currentPage,
      pageSize,
      uniqueTypes,
      availableMonths,
      filteredExpenses,
      groupedExpenses,
      paginatedExpenses,
      totalPages,
      visiblePages,
      statistics,
      getTypeColor,
      handleSearch,
      resetFilters,
      changePage,
      handleEdit,
      handleDelete,
      sortBy,
      refreshData,
      sortField,
      sortOrder
    };
  }
};
</script>

<style scoped src="../styles/components/ExpenseList.css"></style>







