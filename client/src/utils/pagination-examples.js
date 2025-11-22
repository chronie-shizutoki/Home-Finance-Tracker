/**
 * 分页数据获取使用示例
 * 展示如何在现有组件中使用新的分页工具
 */

import { 
  fetchAllPages, 
  createCancellationController,
  getPaginationInfo 
} from '@/utils/pagination';

// 示例1: 在HomeView中使用分页加载
export const usePaginatedExpenseData = () => {
  const Expenses = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  const progress = ref({ loaded: 0, total: 0, progress: 0 });

  // 创建取消控制器
  let cancellationController = null;

  // 分页加载消费数据
  const loadExpenses = async (showProgress = true) => {
    if (isLoading.value) return;

    // 取消之前的请求
    if (cancellationController) {
      cancellationController.abort();
    }

    // 创建新的取消控制器
    cancellationController = createCancellationController();
    isLoading.value = true;
    error.value = null;

    try {
      // 获取分页统计信息
      const stats = await getExpenseStats();
      const paginationInfo = getPaginationInfo(stats.total, 500);
      
      console.log('开始分页加载:', paginationInfo);

      const data = await fetchAllPages({
        apiCall: ({ page, limit }) => 
          axios.get(`/api/expenses?page=${page}&limit=${limit}`),
        pageSize: 500,
        maxConcurrent: 3,
        signal: cancellationController.signal,
        onProgress: showProgress ? (progressData) => {
          progress.value = progressData;
          console.log(`加载进度: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
        } : undefined,
        onError: (err) => {
          console.error('分页加载错误:', err);
        }
      });

      // 格式化数据
      Expenses.value = data
        .map(item => ({
          type: item.type?.trim() || item.type,
          remark: item.remark?.trim() || item.remark,
          amount: Number(item.amount),
          date: item.date
        }))
        .filter(item => !isNaN(item.amount) && item.amount > 0);

      console.log(`分页加载完成: 共${Expenses.value.length}条有效数据`);

    } catch (err) {
      if (err.message !== '操作已被取消') {
        console.error('分页加载失败:', err);
        error.value = `加载失败: ${err.message}`;
      } else {
        console.log('分页加载被取消');
      }
    } finally {
      isLoading.value = false;
      progress.value = { loaded: 0, total: 0, progress: 0 };
    }
  };

  // 获取统计信息
  const getExpenseStats = async () => {
    try {
      const response = await axios.get('/api/expenses?limit=1');
      const total = response.data?.total || response.data?.data?.length || 0;
      return { total };
    } catch (err) {
      console.warn('无法获取统计信息:', err);
      return { total: 1000 }; // 默认值
    }
  };

  // 取消加载
  const cancelLoad = () => {
    if (cancellationController) {
      cancellationController.abort();
    }
  };

  // 清理资源
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

// 示例2: 在ChartsView中使用分页加载
export const usePaginatedChartsData = () => {
  const expenses = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  let cancellationController = null;

  const loadChartData = async () => {
    if (isLoading.value) return;

    // 取消之前的请求
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
        maxConcurrent: 2, // 图表页面可以减少并发数
        signal: cancellationController.signal,
        onProgress: (progressData) => {
          console.log(`图表数据加载进度: ${progressData.progress}%`);
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

      console.log(`图表数据加载完成: 共${expenses.value.length}条数据`);

    } catch (err) {
      if (err.message !== '操作已被取消') {
        console.error('图表数据加载失败:', err);
        error.value = `加载失败: ${err.message}`;
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

// 示例3: 智能数据加载策略
export const useSmartDataLoading = () => {
  const loadData = async (type = 'normal') => {
    switch (type) {
      case 'home':
        return usePaginatedExpenseData();
      case 'charts':
        return usePaginatedChartsData();
      case 'quick':
        // 快速加载，只获取最近的数据
        return fetchAllPages({
          apiCall: ({ page, limit }) => 
            axios.get(`/api/expenses?page=${page}&limit=${limit}&sort=time&order=desc`),
          pageSize: 100,
          maxConcurrent: 1,
          enableProgress: false
        });
      case 'full':
        // 完整加载，适合数据导出等场景
        return fetchAllPages({
          apiCall: ({ page, limit }) => 
            axios.get(`/api/expenses?page=${page}&limit=${limit}`),
          pageSize: 1000,
          maxConcurrent: 5,
          retryTimes: 5
        });
      default:
        throw new Error(`未知的加载类型: ${type}`);
    }
  };

  return { loadData };
};