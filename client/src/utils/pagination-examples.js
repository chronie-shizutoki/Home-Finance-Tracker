/**
 * Pagination Examples Utility
 * @module utils/pagination-examples
 */

import { 
  fetchAllPages, 
  createCancellationController,
  getPaginationInfo 
} from '@/utils/pagination';

// Example 1: Use pagination in HomeView
export const usePaginatedExpenseData = () => {
  const Expenses = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  const progress = ref({ loaded: 0, total: 0, progress: 0 });

  // Create cancellation controller to handle asynchronous requests
  let cancellationController = null;

  // Load paginated expense data
  const loadExpenses = async (showProgress = true) => {
    if (isLoading.value) return;

    // Cancel previous requests
    if (cancellationController) {
      cancellationController.abort();
    }

    // Create new cancellation controller
    cancellationController = createCancellationController();
    isLoading.value = true;
    error.value = null;

    try {
      // Get pagination statistics
      const stats = await getExpenseStats();
      const paginationInfo = getPaginationInfo(stats.total, 500);
      
      console.log('Start pagination load:', paginationInfo);

      const data = await fetchAllPages({
        apiCall: ({ page, limit }) => 
          axios.get(`/api/expenses?page=${page}&limit=${limit}`),
        pageSize: 500,
        maxConcurrent: 3,
        signal: cancellationController.signal,
        onProgress: showProgress ? (progressData) => {
          progress.value = progressData;
          console.log(`Loading progress: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
        } : undefined,
        onError: (err) => {
          console.error('Pagination load error:', err);
        }
      });

      // Format data
      Expenses.value = data
        .map(item => ({
          type: item.type?.trim() || item.type,
          remark: item.remark?.trim() || item.remark,
          amount: Number(item.amount),
          date: item.date
        }))
        .filter(item => !isNaN(item.amount) && item.amount > 0);

      console.log(`Pagination load completed: ${Expenses.value.length} valid data`);

    } catch (err) {
      if (err.message !== 'Operation canceled') {
        console.error('Pagination load failed:', err);
        error.value = `Loading failed: ${err.message}`;
      } else {
        console.log('Pagination load canceled');
      }
    } finally {
      isLoading.value = false;
      progress.value = { loaded: 0, total: 0, progress: 0 };
    }
  };

  // Get statistics data
  const getExpenseStats = async () => {
    try {
      const response = await axios.get('/api/expenses?limit=1');
      const total = response.data?.total || response.data?.data?.length || 0;
      return { total };
    } catch (err) {
      console.warn('Failed to get statistics data:', err);
      return { total: 1000 }; // Default value if request fails
    }
  };

  // Cancel load
  const cancelLoad = () => {
    if (cancellationController) {
      cancellationController.abort();
    }
  };

  // Cleanup resources
  const cleanup = () => {
    cancelLoad();
    cancellationController = null;
  };

  return {
    Expenses,
    isLoading,
    error,
    progress,
    loadExpenses,
    cancelLoad,
    cleanup
  };
};

// Example 2: Use pagination in ChartsView
export const usePaginatedChartsData = () => {
  const expenses = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  let cancellationController = null;

  const loadChartData = async () => {
    if (isLoading.value) return;

    // Cancel previous requests
    if (cancellationController) {
      cancellationController.abort();
    }

    cancellationController = createCancellationController();
    isLoading.value = true;
    error.value = null;

    try {
      const data = await fetchAllPages({
        apiCall: ({ page, limit }) => 
          axios.get(`/api/expenses?page=${page}&limit=${limit}`),
        pageSize: 500,
        maxConcurrent: 2, // Charts page can reduce concurrent requests
        signal: cancellationController.signal,
        onProgress: (progressData) => {
          console.log(`Loading progress: ${progressData.progress}%`);
        }
      });

      expenses.value = data
        .map(item => ({
          type: item.type?.trim() || item.type,
          remark: item.remark?.trim() || item.remark,
          amount: Number(item.amount),
          date: item.date
        }))
        .filter(item => !isNaN(item.amount) && item.amount > 0);

      console.log(`Pagination load completed: ${expenses.value.length} valid data`);

    } catch (err) {
      if (err.message !== 'Operation canceled') {
        console.error('Pagination load failed:', err);
        error.value = `Loading failed: ${err.message}`;
      }
    } finally {
      isLoading.value = false;
    }
  };

  const cancelLoad = () => {
    if (cancellationController) {
      cancellationController.abort();
    }
  };

  const cleanup = () => {
    cancelLoad();
    cancellationController = null;
  };

  return {
    expenses,
    isLoading,
    error,
    loadChartData,
    cancelLoad,
    cleanup
  };
};

// Example 3: Smart data loading strategy
export const useSmartDataLoading = () => {
  const loadData = async (type = 'normal') => {
    switch (type) {
      case 'home':
        return usePaginatedExpenseData();
      case 'charts':
        return usePaginatedChartsData();
      case 'quick':
        // Quick load, only get recent data
        return fetchAllPages({
          apiCall: ({ page, limit }) => 
            axios.get(`/api/expenses?page=${page}&limit=${limit}&sort=time&order=desc`),
          pageSize: 100,
          maxConcurrent: 1,
          enableProgress: false
        });
      case 'full':
        // Full load, suitable for data export scenarios
        return fetchAllPages({
          apiCall: ({ page, limit }) => 
            axios.get(`/api/expenses?page=${page}&limit=${limit}`),
          pageSize: 1000,
          maxConcurrent: 5,
          retryTimes: 5
        });
      default:
        throw new Error(`Unknown load type: ${type}`);
    }
  };

  return { loadData };
};