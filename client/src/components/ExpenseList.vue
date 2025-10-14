<!-- ExpenseList.vue -->
<template>
  <div class="expense-list">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <div class="loader"></div>
      <p>{{ $t('expense.loading') }}</p>
    </div>

    <template v-else>
      <!-- 搜索组件 -->
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
        @search="handleSearch"
      />

      <!-- 空状态提示 -->
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
        <!-- 统计组件 -->
        <ExpenseStats :statistics="statistics" />

        <!-- 表格组件 -->
        <ExpenseTable
          :expenses="paginatedExpenses"
          :sort-field="searchParams.sortOption === 'dateAsc' || searchParams.sortOption === 'dateDesc' ? 'time' : 'amount'"
          :sort-order="searchParams.sortOption === 'dateAsc' || searchParams.sortOption === 'amountAsc' ? 'asc' : 'desc'"
          @sort="sortBy"
        />

        <!-- 分页组件 -->
        <ExpensePagination
          v-if="totalPages > 1"
          :currentPage="currentPage"
          :totalPages="totalPages"
          :visiblePages="visiblePages"
          @page-change="changePage"
        />
      </template>
    </template>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch, watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';
import * as math from 'mathjs';
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

  setup () {
    const { t } = useI18n();
    const searchComponent = ref(null);
    const loading = ref(true);

    // 统一搜索参数
    const searchParams = ref({
      keyword: '',
      type: '',
      month: '',
      minAmount: null,
      maxAmount: null,
      sortOption: 'dateDesc'
    });

    // 分页相关状态
    const currentPage = ref(1);
    const pageSize = ref(10);
    const totalItems = ref(0);
    const expenses = ref([]);
    
    // 添加缺失的变量定义
    const uniqueTypes = ref([]);
    const availableMonths = ref([]);
    
    // 实现实际的筛选逻辑
    const filteredExpenses = computed(() => {
      if (!expenses.value || expenses.value.length === 0) return [];
      
      return expenses.value.filter(expense => {
        // 关键词筛选
        if (searchParams.value.keyword) {
          const keyword = searchParams.value.keyword.toLowerCase();
          const hasKeyword = 
            (expense.remark && expense.remark.toLowerCase().includes(keyword)) ||
            (expense.type && expense.type.toLowerCase().includes(keyword)) ||
            (expense.amount && expense.amount.toString().includes(keyword));
          if (!hasKeyword) return false;
        }
        
        // 类型筛选
        if (searchParams.value.type && expense.type !== searchParams.value.type) {
          return false;
        }
        
        // 月份筛选
        if (searchParams.value.month) {
          const expenseDate = new Date(expense.date || expense.time);
          const expenseMonth = `${expenseDate.getFullYear()}-${String(expenseDate.getMonth() + 1).padStart(2, '0')}`;
          if (expenseMonth !== searchParams.value.month) {
            return false;
          }
        }
        
        // 金额范围筛选
        const amount = Number(expense.amount);
        if (searchParams.value.minAmount !== null && amount < searchParams.value.minAmount) {
          return false;
        }
        if (searchParams.value.maxAmount !== null && amount > searchParams.value.maxAmount) {
          return false;
        }
        
        return true;
      });
    });

    // 获取分页数据
    const fetchPaginatedData = async () => {
      loading.value = true;
      try {
        // 添加排序参数到请求中
        const params = new URLSearchParams();
        params.append('page', currentPage.value);
        params.append('limit', pageSize.value);
        
        // 添加搜索参数
        if (searchParams.value.keyword) params.append('keyword', searchParams.value.keyword);
        if (searchParams.value.type) params.append('type', searchParams.value.type);
        if (searchParams.value.month) params.append('month', searchParams.value.month);
        // 验证金额参数是否为有效数字
        const minAmount = parseFloat(searchParams.value.minAmount);
        const maxAmount = parseFloat(searchParams.value.maxAmount);
        if (searchParams.value.minAmount !== null && !isNaN(minAmount)) {
          params.append('minAmount', minAmount.toString());
        }
        if (searchParams.value.maxAmount !== null && !isNaN(maxAmount)) {
          params.append('maxAmount', maxAmount.toString());
        }
        if (searchParams.value.sortOption) params.append('sort', searchParams.value.sortOption);
        
        const response = await ExpenseAPI.getExpenses(currentPage.value, pageSize.value, params);
        
        // 适配后端返回的分页格式
        if (response && response.data && response.data.data && Array.isArray(response.data.data)) {
          expenses.value = response.data.data;
          totalItems.value = response.data.total || 0;
          
          // 更新类型和月份数据
          if (response.data.meta) {
            uniqueTypes.value = response.data.meta.uniqueTypes || [];
            availableMonths.value = response.data.meta.availableMonths || [];
          } else {
            // 从当前页数据中提取类型和月份信息
            const types = [...new Set(expenses.value.map(e => e.type))];
            const months = [...new Set(expenses.value.map(e => {
              const date = new Date(e.date || e.time);
              return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
            }))];
            
            uniqueTypes.value = types.filter(type => type && type.trim() !== '');
            availableMonths.value = months.filter(month => month && month.trim() !== '');
          }
        } else if (Array.isArray(response)) {
          // 兼容旧格式
          expenses.value = response;
          totalItems.value = response.length;
        }
      } catch (error) {
        console.error('获取分页数据失败:', error);
      } finally {
        loading.value = false;
      }
    };

    // 搜索处理
    const handleSearch = (params) => {
      searchParams.value = { ...params };
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics(); // 更新统计数据
    };

    // 重置筛选条件
    const resetFilters = () => {
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
      fetchStatistics(); // 更新统计数据
    };

    // 分页处理
    const paginatedExpenses = computed(() => {
      return expenses.value;
    });

    // 总页数
    const totalPages = computed(() => {
      return Math.ceil(totalItems.value / pageSize.value) || 1;
    });

    // 引入 window 以方便测试模拟
    const { innerWidth } = window;
    // 可见页码（小屏幕最多显示3个，大屏幕最多显示5个）
    const visiblePages = computed(() => {
      const pages = [];
      const total = totalPages.value;
      const current = currentPage.value;
      // 根据屏幕宽度确定最多显示的页码数
      const maxVisible = innerWidth < 768 ? 1 : 5;

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

    // 使用ref存储从后端获取的统计数据，而不是基于当前页数据计算
    const statistics = ref({
      count: 0,
      totalAmount: '0.00',
      averageAmount: '0.00',
      medianAmount: '0.00',
      minAmount: '0.00',
      maxAmount: '0.00',
      uniqueTypeCount: 0,
      typeDistribution: {}
    });

    // 定义获取统计数据的函数
    const fetchStatistics = async () => {
      try {
        // 构建与getExpenses相同的查询参数
        const statsSearchParams = new URLSearchParams();
        if (searchParams.value.keyword) statsSearchParams.set('keyword', searchParams.value.keyword);
        if (searchParams.value.type) statsSearchParams.set('type', searchParams.value.type);
        if (searchParams.value.month) statsSearchParams.set('month', searchParams.value.month);
        
        // 添加金额范围参数，确保是有效数字
        const validMinAmount = parseFloat(searchParams.value.minAmount);
        const validMaxAmount = parseFloat(searchParams.value.maxAmount);
        if (!isNaN(validMinAmount)) {
          statsSearchParams.set('minAmount', validMinAmount.toString());
        }
        if (!isNaN(validMaxAmount)) {
          statsSearchParams.set('maxAmount', validMaxAmount.toString());
        }

        // 调用后端统计API获取完整数据
        const statsData = await ExpenseAPI.getStatistics(statsSearchParams);
        
        // 格式化数字以保持一致性
        if (statsData && !statsData.error) {
          statistics.value = {
            count: statsData.count || 0,
            totalAmount: (statsData.totalAmount || 0).toFixed(2),
            averageAmount: (statsData.averageAmount || 0).toFixed(2),
            medianAmount: (statsData.medianAmount || 0).toFixed(2),
            minAmount: (statsData.minAmount || 0).toFixed(2),
            maxAmount: (statsData.maxAmount || 0).toFixed(2),
            uniqueTypeCount: statsData.uniqueTypeCount || 0,
            typeDistribution: statsData.typeDistribution || {}
          };
        }
      } catch (error) {
        console.error('获取统计数据失败:', error);
        // 出错时保持原有的统计数据
      }
    };

    // 当筛选条件变化时重新获取统计数据
    watchEffect(() => {
      // 明确引用所有需要监听的筛选条件
      const { keyword, type, month, minAmount, maxAmount, sortOption } = searchParams.value;
      
      // 延迟执行，避免频繁请求
      const timer = setTimeout(() => {
        fetchStatistics();
      }, 300);
      
      return () => clearTimeout(timer);
    });

    // 初始加载时获取统计数据
    fetchStatistics();

    // 页面切换方法
    const changePage = (page) => {
      if (page >= 1 && page <= totalPages.value) {
        currentPage.value = page;
        fetchPaginatedData();
        // 查找可排序元素
        const sortableElement = document.querySelector('.sortable');
        // 查找头部元素，假设头部类名为 header
        const headerElement = document.querySelector('.header');
        // 获取头部高度，如果没找到头部元素则默认为 0
        const headerHeight = headerElement ? headerElement.offsetHeight : 0;

        if (sortableElement) {
          // 获取可排序元素相对于视口的位置
          const rect = sortableElement.getBoundingClientRect();
          // 计算滚动目标位置，考虑头部高度
          const scrollTop = window.pageYOffset + rect.top - headerHeight;

          // 平滑滚动到计算后的位置
          window.scrollTo({
            top: scrollTop,
            behavior: 'smooth'
          });
        }
      }
    };

    // 排序方法
    const sortBy = (field) => {
      const currentSort = searchParams.value.sortOption;
      let newSort = '';

      if (field === 'time') {
        newSort = currentSort === 'dateAsc' ? 'dateDesc' : 'dateAsc';
      } else if (field === 'amount') {
        newSort = currentSort === 'amountAsc' ? 'amountDesc' : 'amountAsc';
      }

      if (newSort) {
        searchParams.value.sortOption = newSort;
        currentPage.value = 1;
        fetchPaginatedData();
      }
    };

    // 监听pageSize变化
    watch(pageSize, () => {
      currentPage.value = 1;
      fetchPaginatedData();
    });

    // 初始化时加载数据
    onMounted(() => {
      fetchPaginatedData();
    });

    return {
      searchComponent,
      loading,
      searchParams,
      currentPage,
      pageSize,
      uniqueTypes,
      availableMonths,
      filteredExpenses,
      paginatedExpenses,
      totalPages,
      visiblePages,
      statistics,
      getTypeColor,
      handleSearch,
      resetFilters,
      changePage,
      sortBy
    };
  }
};
</script>

<style scoped>
.expense-list {
  display: flex;
  flex-direction: column;
  gap: 25px;
  padding: 0 10px;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 20px;
}

.loader {
  width: 50px;
  height: 50px;
  border: 5px solid #f3f3f3;
  border-top: 5px solid #4361ee;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px 20px;
  background: transparent;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-top: 20px;
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: #f0f7ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-icon svg {
  width: 40px;
  height: 40px;
  fill: #4361ee;
}

.empty-state h3 {
  margin: 0 0 10px;
  font-size: 1.4rem;
  color: #2c3e50;
}

.empty-state p {
  margin: 0 0 20px;
  color: #6c757d;
  max-width: 500px;
  line-height: 1.6;
}

.reset-button {
  padding: 10px 25px;
  background: linear-gradient(135deg, #4361ee, #3a56d4);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.reset-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(67, 97, 238, 0.3);
}

@media (max-width: 768px) {
  .expense-list {
    gap: 15px;
  }

  .empty-state {
    padding: 30px 15px;
  }
}
</style>
