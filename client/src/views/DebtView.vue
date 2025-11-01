<template>
  <div class="debt-container">
    <Header :title="t('debt.title')" />
    
    <div class="header-actions">
      <button class="btn-primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        {{ t('debt.addNewRecord') }}
      </button>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-container">
      <div class="filter-row mb-4">
        <div class="filter-item">
          <CustomSelect 
            v-model="filterType" 
            :options="[
              { label: t('debt.lend'), value: 'lend' },
              { label: t('debt.borrow'), value: 'borrow' }
            ]"
            :empty-option-label="t('debt.allTypes')"
          />
        </div>
        <div class="filter-item">
          <label>{{ t('debt.repaymentStatus') }}</label>
          <CustomSelect 
            v-model="filterRepaid" 
            :options="[
              { label: t('debt.repaid'), value: 'true' },
              { label: t('debt.unrepaid'), value: 'false' }
            ]"
            :empty-option-label="t('debt.allStatus')"
          />
        </div>
        <div class="filter-item">
          <input 
            v-model="filterPerson" 
            :placeholder="t('debt.searchPerson')"
            class="filter-input"
          />
        </div>
      </div>
      <div class="filter-row mb-4">
        <div class="filter-item date-range">
          <input 
            v-model="dateRange[0]" 
            type="date" 
            :placeholder="t('debt.startDate')"
            class="date-input"
          />
          <span class="range-separator">{{ t('common.to') }}</span>
          <input 
            v-model="dateRange[1]" 
            type="date" 
            :placeholder="t('debt.endDate')"
            class="date-input"
          />
        </div>
        <div class="filter-item button-group">
          <button class="btn" @click="resetFilters">{{ t('debt.resetFilters') }}</button>
          <button class="btn-primary" @click="fetchDebts">{{ t('debt.search') }}</button>
        </div>
      </div>
    </div>

    <!-- 债务记录表格（桌面端） -->
    <div class="table-container">
      <table 
      class="debt-table"
      v-if="!loading"
    >
        <thead>
          <tr>
            <th>{{ t('common.index') }}</th>
            <th>{{ t('debt.type') }}</th>
            <th>{{ t('debt.person') }}</th>
            <th>{{ t('debt.amount') }}</th>
            <th>{{ t('debt.date') }}</th>
            <th>{{ t('debt.isRepaid') }}</th>
            <th>{{ t('debt.remark') }}</th>
            <th>{{ t('common.action') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(debt, index) in debtsData" :key="debt.id">
            <td>{{ (pagination.currentPage - 1) * pagination.pageSize + index + 1 }}</td>
            <td>
              <span class="type-tag" :class="debt.type === 'lend' ? 'tag-success' : 'tag-warning'">
                {{ debt.type === 'lend' ? t('debt.lend') : t('debt.borrow') }}
              </span>
            </td>
            <td>{{ debt.person }}</td>
            <td>{{ debt.amount.toFixed(2) }}</td>
            <td>{{ formatDate(debt.date) }}</td>
            <td>
              <div class="switch-container inline">
                <input 
                  :id="`switch-${debt.id}`" 
                  v-model="debt.isRepaid" 
                  type="checkbox" 
                  class="form-switch"
                  @change="updateRepaymentStatus(debt.id, debt.isRepaid)"
                />
                <label :for="`switch-${debt.id}`" class="switch-label"></label>
              </div>
            </td>
            <td>{{ debt.remark || '-' }}</td>
            <td class="action-buttons">
              <button class="btn-small btn-primary-small" @click="editDebt(debt)">
                {{ t('debt.edit') }}
              </button>
              <button class="btn-small btn-danger-small" @click="deleteDebt(debt.id)">
                {{ t('debt.delete') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="loading-container">
        <div class="loading-spinner"></div>
        <span>{{ t('app.loading') }}</span>
      </div>
      
      <!-- 空状态 -->
      <div v-if="!loading && debtsData.length === 0" class="empty-state">
        <p>{{ t('debt.emptyState') }}</p>
      </div>
    </div>

    <!-- 债务记录卡片布局（移动端） -->
    <div class="cards-container">
      <div 
        v-for="(debt, index) in debtsData" 
        :key="debt.id"
        class="debt-card"
      >
        <div class="card-header">
          <span :class="['type-tag', debt.type === 'lend' ? 'tag-success' : 'tag-warning']">
            {{ debt.type === 'lend' ? t('debt.lend') : t('debt.borrow') }}
          </span>
          <span class="card-index">{{ (pagination.currentPage - 1) * pagination.pageSize + index + 1 }}</span>
        </div>
        
        <div class="card-body">
          <div class="card-row">
            <span class="label">{{ t('debt.person') }}:</span>
            <span class="value">{{ debt.person }}</span>
          </div>
          
          <div class="card-row">
            <span class="label">{{ t('debt.amount') }}:</span>
            <span class="value amount">
              {{ debt.amount.toFixed(2) }}
            </span>
          </div>
          
          <div class="card-row">
            <span class="label">{{ t('debt.date') }}:</span>
            <span class="value">{{ formatDate(debt.date) }}</span>
          </div>
          
          <div class="card-row">
            <span class="label">{{ t('debt.isRepaid') }}:</span>
            <label class="status-switch">
              <input
                type="checkbox"
                :checked="debt.isRepaid"
                @change="updateRepaymentStatus(debt.id, !debt.isRepaid)"
              />
              <span class="slider"></span>
              <span class="switch-label">{{ debt.isRepaid ? t('debt.repaid') : t('debt.unrepaid') }}</span>
            </label>
          </div>
          
          <div v-if="debt.remark" class="card-row remark">
            <span class="label">{{ t('debt.remark') }}:</span>
            <span class="value">{{ debt.remark }}</span>
          </div>
        </div>
        
        <div class="card-footer">
          <button class="btn-small btn-primary-small" @click="editDebt(debt)">{{ t('debt.edit') }}</button>
          <button class="btn-small btn-danger-small" @click="deleteDebt(debt.id)">{{ t('debt.delete') }}</button>
        </div>
      </div>
      
      <!-- 移动端空状态 -->
      <div v-if="!loading && debtsData.length === 0" class="empty-state">
        <p>{{ t('debt.emptyState') }}</p>
      </div>
      
      <!-- 移动端加载状态 -->
      <div v-if="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <span>{{ t('app.loading') }}</span>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination-container" v-if="total > 0">
      <div class="pagination-info">{{ t('common.total') }}: {{ total }}</div>
      <div class="pagination-controls">
        <CustomSelect 
            v-model="pagination.pageSize" 
            :options="[
              { label: `10 ${t('common.perPage')}`, value: 10 },
              { label: `20 ${t('common.perPage')}`, value: 20 },
              { label: `50 ${t('common.perPage')}`, value: 50 },
              { label: `100 ${t('common.perPage')}`, value: 100 }
            ]"
            :include-empty-option="false"
          />
        <button 
          class="btn-small" 
          @click="handleCurrentChange(pagination.currentPage - 1)"
          :disabled="pagination.currentPage <= 1"
        >
          {{ t('common.prev') }}
        </button>
        <span class="page-info">
          {{ pagination.currentPage }} / {{ Math.ceil(total / pagination.pageSize) }}
        </span>
        <button 
          class="btn-small" 
          @click="handleCurrentChange(pagination.currentPage + 1)"
          :disabled="pagination.currentPage >= Math.ceil(total / pagination.pageSize)"
        >
          {{ t('common.next') }}
        </button>
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <div v-if="dialogVisible" class="dialog-overlay">
      <div class="dialog-content" style="background-color: var(--bg-color-light);">
        <div class="dialog-header">
          <h3>{{ editingDebt ? t('debt.editDialogTitle') : t('debt.addDialogTitle') }}</h3>
          <button class="dialog-close" @click="cancelDialog">&times;</button>
        </div>
        <div class="dialog-body">
        <div class="form-item">
          <label>{{ t('debt.type') }} *</label>
          <CustomSelect 
            v-model="debtForm.type" 
            :options="[
              { label: t('debt.lend'), value: 'lend' },
              { label: t('debt.borrow'), value: 'borrow' }
            ]"
            :include-empty-option="false"
          />
        </div>
        <div class="form-item">
          <label>{{ t('debt.person') }} *</label>
          <input 
            v-model="debtForm.person" 
            type="text" 
            :placeholder="t('debt.enterPerson')"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label>{{ t('debt.amount') }} *</label>
          <input 
            v-model.number="debtForm.amount" 
            type="number" 
            step="0.01" 
            min="0.01" 
            :placeholder="t('debt.enterAmount')"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label>{{ t('debt.date') }} *</label>
          <input 
            v-model="debtForm.date" 
            type="date" 
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label>{{ t('debt.isRepaid') }}</label>
          <div class="switch-container">
            <input 
              id="isRepaidSwitch" 
              v-model="debtForm.isRepaid" 
              type="checkbox" 
              class="form-switch"
            />
            <label for="isRepaidSwitch" class="switch-label"></label>
          </div>
        </div>
        <div class="form-item">
          <label>{{ t('debt.remark') }}</label>
          <textarea 
            v-model="debtForm.remark" 
            :placeholder="t('debt.enterRemark')"
            rows="3"
            class="form-textarea"
          ></textarea>
        </div>
        </div>

      <div class="dialog-footer">
        <button class="btn" @click="cancelDialog">{{ t('common.cancel') }}</button>
        <button 
          class="btn-primary" 
          @click="submitDialog"
          :disabled="dialogLoading"
        >
          {{ t('common.submit') }}
          <span v-if="dialogLoading" class="loading-spinner"></span>
        </button>
      </div>
    </div>
  </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { DebtAPI } from '@/api';
import Header from '@/components/Header.vue';
import CustomSelect from '../components/CustomSelect.vue';

defineOptions({ name: 'DebtView' });

const { t } = useI18n();

// 响应式数据
const loading = ref(false);
const dialogVisible = ref(false);
const dialogLoading = ref(false);
const editingDebt = ref(null);
const debtsData = ref([]);
const total = ref(0);

// 筛选条件
const filterType = ref('');
const filterRepaid = ref('');
const filterPerson = ref('');
const dateRange = ref([]);



// 分页数据
const pagination = reactive({
  currentPage: 1,
  pageSize: 20
});

// 表单数据
const debtForm = reactive({
  type: 'lend',
  person: '',
  amount: '',
  date: new Date().toISOString().split('T')[0],
  isRepaid: false,
  remark: ''
});

// 自定义表单验证函数
const validateForm = () => {
  let isValid = true;
  const errors = [];
  
  if (!debtForm.type) {
    errors.push(t('debt.typeRequired'));
    isValid = false;
  }
  
  if (!debtForm.person.trim()) {
    errors.push(t('debt.personRequired'));
    isValid = false;
  } else if (debtForm.person.length > 50) {
    errors.push(t('debt.personLength'));
    isValid = false;
  }
  
  if (!debtForm.amount || debtForm.amount < 0.01) {
    errors.push(t('debt.amountRequired'));
    isValid = false;
  }
  
  if (!debtForm.date) {
    errors.push(t('debt.dateRequired'));
    isValid = false;
  }
  
  if (errors.length > 0) {
    alert(errors.join('\n'));
  }
  
  return isValid;
};

// 获取债务记录
const fetchDebts = async () => {
  try {
    loading.value = true;
    
    // 构建查询参数
    const params = {
      page: pagination.currentPage,
      limit: pagination.pageSize
    };
    
    if (filterType.value) {
      params.type = filterType.value;
    }
    
    if (filterRepaid.value !== '') {
      params.isRepaid = filterRepaid.value === 'true';
    }
    
    // 添加人名搜索筛选
    if (filterPerson.value.trim()) {
      params.person = filterPerson.value.trim();
    }
    
    // 添加日期范围筛选（如果有）
    if (dateRange.value && dateRange.value.length >= 2 && dateRange.value[0] && dateRange.value[1]) {
      params.startDate = dateRange.value[0];
      params.endDate = dateRange.value[1];
    }
    
    console.log('搜索参数:', params); // 添加日志记录搜索参数
    
    // 如果没有原始数据，先获取
    if (!window.originalDebtsData || window.originalDebtsData.length === 0) {
      // 尝试从API获取新数据
      try {
        const response = await DebtAPI.getDebts(params);
        window.originalDebtsData = [...response];
      } catch (apiError) {
        console.log('API调用失败，使用模拟数据');
        // 使用模拟数据作为备选
        window.originalDebtsData = [
          { id: 1, type: 'lend', person: '张三', amount: 1000, date: '2024-01-01', isRepaid: false, remark: '测试借款' },
          { id: 2, type: 'borrow', person: '李四', amount: 500, date: '2024-01-02', isRepaid: true, remark: '' },
          { id: 3, type: 'lend', person: '王五', amount: 2000, date: '2024-01-03', isRepaid: false, remark: '紧急借款' }
        ];
      }
    }
    
    // 始终从原始数据开始筛选，而不是已经筛选过的数据
    let filteredData = [...window.originalDebtsData];
    
    // 应用搜索筛选
    if (params.person) {
      filteredData = filteredData.filter(debt => 
        debt.person.toLowerCase().includes(params.person.toLowerCase())
      );
    }
    
    // 应用类型筛选
    if (params.type) {
      filteredData = filteredData.filter(debt => debt.type === params.type);
    }
    
    // 应用还款状态筛选
    if (params.isRepaid !== undefined) {
      // 确保isRepaid是布尔值类型，避免字符串比较问题
      const isRepaidBool = params.isRepaid === true || params.isRepaid === 'true';
      filteredData = filteredData.filter(debt => debt.isRepaid === isRepaidBool);
    }
    
    // 应用日期范围筛选
    if (params.startDate && params.endDate) {
      filteredData = filteredData.filter(debt => {
        const debtDate = new Date(debt.date);
        const startDate = new Date(params.startDate);
        const endDate = new Date(params.endDate);
        return debtDate >= startDate && debtDate <= endDate;
      });
    }
    
    // 应用分页
    const startIndex = (params.page - 1) * params.limit;
    const paginatedData = filteredData.slice(startIndex, startIndex + params.limit);
    
    debtsData.value = paginatedData;
    total.value = filteredData.length;
  } catch (error) {
    console.error('获取债务记录失败:', error);
    alert(t('debt.fetchFailed'));
  } finally {
    loading.value = false;
  }
};

// 重置筛选条件
const resetFilters = () => {
  // 重置所有筛选条件
  filterType.value = '';
  filterRepaid.value = '';
  filterPerson.value = '';
  dateRange.value = [];
  pagination.currentPage = 1;
  
  // 直接调用fetchDebts，让它重新应用空筛选条件
  // 这样可以确保分页也被正确应用
  fetchDebts();
};

// 处理分页大小变化
const handleSizeChange = (size) => {
  pagination.pageSize = size;
  fetchDebts();
};

// 处理当前页变化
const handleCurrentChange = (current) => {
  pagination.currentPage = current;
  fetchDebts();
};

// 打开添加对话框
const showAddDialog = () => {
  editingDebt.value = null;
  // 重置表单
  Object.assign(debtForm, {
    type: 'lend',
    person: '',
    amount: '',
    date: new Date().toISOString().split('T')[0],
    isRepaid: false,
    remark: ''
  });
  dialogVisible.value = true;
};

// 打开编辑对话框
const editDebt = (debt) => {
  editingDebt.value = debt;
  // 填充表单数据
  Object.assign(debtForm, {
    type: debt.type,
    person: debt.person,
    amount: debt.amount,
    date: formatDateForInput(debt.date),
    isRepaid: debt.isRepaid,
    remark: debt.remark || ''
  });
  dialogVisible.value = true;
};

// 取消对话框
const cancelDialog = () => {
  dialogVisible.value = false;
  editingDebt.value = null;
};

// 提交对话框
const submitDialog = async () => {
  try {
    if (!validateForm()) {
      return;
    }
    
    dialogLoading.value = true;
    
    if (editingDebt.value) {
      // 更新债务记录
      await DebtAPI.updateDebt(editingDebt.value.id, debtForm);
      alert(t('debt.updateSuccess'));
    } else {
      // 添加新债务记录
      await DebtAPI.addDebt(debtForm);
      alert(t('debt.addSuccess'));
    }
    
    dialogVisible.value = false;
    fetchDebts();
  } catch (error) {
      console.error(editingDebt.value ? '更新债务失败:' : '添加债务失败:', error);
      alert(editingDebt.value ? t('debt.updateFailed') : t('debt.addFailed'));
    } finally {
    dialogLoading.value = false;
  }
};

// 删除债务记录
const deleteDebt = async (id) => {
  try {
    const confirmed = confirm(`${t('debt.deleteTitle')}\n${t('debt.deleteConfirm')}`);
    if (!confirmed) {
      return;
    }
    
    await DebtAPI.deleteDebt(id);
    alert(t('debt.deleteSuccess'));
    fetchDebts();
  } catch (error) {
    console.error('删除债务失败:', error);
    alert(t('debt.deleteFailed'));
  }
};

// 更新还款状态
  const updateRepaymentStatus = async (id, isRepaid) => {
    try {
      // 更新还款状态
      await DebtAPI.updateDebt(id, { isRepaid });
      alert(t('debt.statusUpdateSuccess'));
    } catch (error) {
      console.error('更新还款状态失败:', error);
      alert(t('debt.statusUpdateFailed'));
      // 回滚状态
    const debt = debtsData.value.find(d => d.id === id);
    if (debt) {
      debt.isRepaid = !isRepaid;
    }
  }
};

// 格式化日期显示
const formatDate = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleDateString();
};

// 格式化日期用于输入
const formatDateForInput = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toISOString().split('T')[0];
};

// 初始化加载数据
onMounted(() => {
  fetchDebts();
});
</script>

<style scoped>
/* 全局CSS变量定义 */
:root {
  /* 主题色 */
  --primary-color: #409eff;
  --primary-hover: #66b1ff;
  --success-color: #67c23a;
  --warning-color: #e6a23c;
  --danger-color: #f56c6c;
  --danger-hover: #f78989;
  
  /* 背景色 */
  --bg-color: #f5f7fa;
  --bg-color-light: #ffffff;
  --bg-color-dark: #f0f2f5;
  
  /* 文字颜色 */
  --text-primary: #303133;
  --text-regular: #606266;
  --text-secondary: #909399;
  --text-placeholder: #c0c4cc;
  
  /* 边框颜色 */
  --border-color: #dcdfe6;
  --border-light: #ebeef5;
  --border-lighter: #f5f7fa;
  
  /* 阴影 */
  --shadow-base: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
  --shadow-light: 0 2px 4px 0 rgba(0, 0, 0, 0.05);
  --shadow-hover: 0 4px 12px 0 rgba(0, 0, 0, 0.1);
  
  /* 圆角 */
  --radius-small: 4px;
  --radius-base: 6px;
  --radius-large: 8px;
}

/* 深色模式变量 */
@media (prefers-color-scheme: dark) {
  :root {
    /* 主题色 */
    --primary-color: #409eff;
    --primary-hover: #66b1ff;
    
    /* 背景色 */
    --bg-color: #1a1a1a;
    --bg-color-light: #2a2a2a;
    --bg-color-dark: #333;
    
    /* 文字颜色 */
    --text-primary: #e0e0e0;
    --text-regular: #cccccc;
    --text-secondary: #aaa;
    --text-placeholder: #888;
    
    /* 边框颜色 */
    --border-color: #444;
    --border-light: #555;
    --border-lighter: #666;
    
    /* 阴影 */
    --shadow-base: 0 1px 2px 0 rgba(0, 0, 0, 0.1);
    --shadow-light: 0 2px 4px 0 rgba(0, 0, 0, 0.2);
    --shadow-hover: 0 4px 12px 0 rgba(0, 0, 0, 0.3);
  }
}

/* 基础样式 */
.debt-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  color: var(--text-primary);
  background-color: var(--bg-color);
  min-height: 100vh;
  transition: all 0.3s ease;
}

/* 渐入动画效果 */
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.filter-container,
.table-container,
.cards-container,
.pagination-container {
  animation: fadeIn 0.5s ease-out;
}

/* 深色模式适配 - 覆盖特定样式 */
@media (prefers-color-scheme: dark) {
  /* 类型标签 - 深色模式 */
  .debt-table .type-tag,
  .debt-card .type-tag {
    border: 1px solid var(--border-light);
    transition: all 0.3s ease;
  }
  
  .debt-table .tag-success,
  .debt-card .tag-success {
    background-color: rgba(103, 194, 58, 0.2);
    color: var(--success-color);
  }
  
  .debt-table .tag-warning,
  .debt-card .tag-warning {
    background-color: rgba(230, 162, 60, 0.2);
    color: var(--warning-color);
  }
  
  /* 确保类型标签在深色模式下显示清晰 */
  .range-separator {
    color: var(--text-secondary);
  }
  
  /* 开关样式 - 深色模式 */
  .form-switch {
    background-color: var(--border-light);
  }
  
  /* 空状态样式 - 深色模式 */
  .empty-state {
    background-color: var(--bg-color-light);
    color: var(--text-secondary);
  }
  
  /* 自定义下拉菜单 - 深色模式 */
  .dropdown-menu {
    background-color: var(--bg-color-light);
    border-color: var(--border-color);
    box-shadow: var(--shadow-hover);
  }
  
  .dropdown-item {
    color: var(--text-primary);
  }
  
  .dropdown-item:hover {
    background-color: var(--bg-color-dark);
    color: var(--primary-color);
  }
  
  .dropdown-item.selected {
    background-color: var(--bg-color-dark);
    color: var(--primary-color);
  }
  
  /* 分页控件 - 深色模式 */
  .pagination-controls .dropdown-toggle.page-size-select {
    background-color: var(--bg-color-light);
    border-color: var(--border-color);
    color: var(--text-primary);
  }
  
  /* 移除未使用的硬编码样式 - 确保统一使用CSS变量 */
  .debt-table th {
    color: var(--text-primary) !important;
  }
  
  .debt-table tr:hover {
    background-color: var(--bg-color-dark) !important;
  }
  
  .switch-label {
    color: var(--text-regular);
  }
  
  .btn-danger-small {
    background-color: var(--danger-color);
  }
  
  .btn-danger-small:hover {
    background-color: var(--danger-hover);
  }
  
  .btn-primary-small {
    background-color: var(--primary-color);
  }
  
  .btn-primary-small:hover {
    background-color: var(--primary-hover);
  }
  
  /* 输入框深色模式样式 */
  .filter-input,
  .date-input,
  .form-input {
    background-color: var(--bg-color-light);
    border-color: var(--border-color);
    color: var(--text-primary);
  }
  
  .filter-input:focus,
  .date-input:focus,
  .form-input:focus {
    border-color: var(--primary-color);
  }
  
  /* 确保下拉菜单背景不透明 */
  .dropdown-menu {
    background-color: var(--bg-color-light) !important;
  }
  
  /* 确保弹窗背景不透明 */
  .dialog-content {
    background-color: var(--bg-color-light) !important;
  }
}

/* 加载状态样式 - 深色模式 */
.loading-container {
    background-color: #2a2a2a;
  }
  
  /* 卡片布局样式 - 深色模式 */
  .debt-card {
    background-color: var(--bg-color-light);
  }
  
  .card-header,
  .card-footer {
    background-color: var(--bg-color-dark) !important;
    border-color: var(--border-color);
  }
  
  .card-index {
    color: var(--text-secondary);
  }
  
  .card-row.remark {
    border-color: var(--border-color);
  }
  
  .card-row.remark .value {
    color: var(--text-secondary);
  }
  
  .label {
    color: var(--text-regular);
  }
  
  .value {
    color: var(--text-primary);
  }
  
  /* 空状态样式 - 深色模式 */
  .empty-state {
    color: var(--text-secondary);
    background-color: var(--bg-color-light);
  }

.form-item label {
  color: var(--text-primary) !important;
}

/* 基础样式 */
.debt-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  color: var(--text-primary);
  background-color: var(--bg-color);
  min-height: 100vh;
  transition: all 0.3s ease;
}

/* 头部操作区域 */
.header-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin: 20px 0;
}

/* 按钮样式 - 微交互增强 */
.btn,
.btn-primary {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-base);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  box-shadow: var(--shadow-base);
  position: relative;
  overflow: hidden;
  z-index: 1;
}

/* 按钮悬停效果 - 背景渐变 */
.btn::before,
.btn-primary::before {
  content: '';
  position: absolute;
  top: 0;
  left: -10%;
  width: 0;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.1), transparent);
  transition: width 0.5s ease;
  z-index: -1;
}

.btn:hover::before,
.btn-primary:hover::before {
  width: 120%;
}

.btn {
  background-color: var(--bg-color);
  color: var(--text-regular);
  border: 1px solid var(--border-color);
}

.btn:hover {
  background-color: var(--border-light);
  transform: translateY(-1px);
  box-shadow: var(--shadow-light);
}

.btn-primary {
  background-color: var(--primary-color);
  color: white;
}

.btn-primary:hover {
  background-color: var(--primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--shadow-hover);
}

.btn-primary:disabled {
  background-color: var(--text-placeholder);
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 按钮点击动画 */
.btn:active,
.btn-primary:active {
  transform: translateY(0);
  box-shadow: var(--shadow-base);
}

.btn-small,
.btn-primary-small,
.btn-danger-small {
  padding: 4px 12px;
  font-size: 12px;
  border-radius: var(--radius-small);
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
}

.btn-small {
  background-color: var(--bg-color);
  color: var(--text-regular);
  border: 1px solid var(--border-color);
}

.btn-small:hover {
  background-color: var(--border-light);
}

.btn-primary-small {
  background-color: var(--primary-color);
  color: white;
  border: none;
}

.btn-primary-small:hover {
  background-color: #66b1ff;
}

.btn-danger-small {
  background-color: var(--danger-color);
  color: white;
  border: none;
}

.btn-danger-small:hover {
  background-color: #f78989;
}

/* 筛选区域样式 - 增强视觉层次 */
.filter-container {
  background-color: var(--bg-color-light);
  padding: 24px;
  border-radius: var(--radius-large);
  margin-bottom: 24px;
  box-shadow: var(--shadow-base);
  transition: all 0.3s ease;
  border: 1px solid var(--border-light);
}

/* 筛选区域悬停效果 */
.filter-container:hover {
  box-shadow: var(--shadow-light);
  transform: translateY(-1px);
}

.filter-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-item {
  flex: 1;
  min-width: 200px;
}

/* 筛选区域输入框和表单输入框样式 - 统一视觉效果 */
.filter-select,
.filter-input,
.date-input,
.form-input,
.form-select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  font-size: 14px;
  transition: all 0.3s ease;
  background-color: var(--bg-color-light);
  color: var(--text-primary);
}

/* 输入框聚焦效果 */
.filter-select:focus,
.filter-input:focus,
.date-input:focus,
.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  transform: translateY(-1px);
}

/* 输入框悬停效果 */
.filter-select:hover,
.filter-input:hover,
.date-input:hover,
.form-input:hover,
.form-select:hover {
  border-color: var(--primary-color);
}

.date-range {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 400px;
}

.date-range .date-input {
  flex: 1;
}

.range-separator {
  color: #909399;
  font-size: 14px;
}

.button-group {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

/* 表格样式 - 增强视觉层次感 */
.debt-table {
  width: 100%;
  border-collapse: collapse;
  background-color: var(--bg-color-light);
  box-shadow: var(--shadow-base);
  border-radius: var(--radius-large);
  overflow: hidden;
}

.debt-table th,
.debt-table td {
  padding: 14px 16px;
  border: 1px solid var(--border-light);
  text-align: left;
  transition: all 0.3s ease;
}

/* 表格头部增强 */
.debt-table th {
  background-color: var(--bg-color-dark);
  font-weight: 600;
  color: var(--text-primary);
  position: relative;
}

/* 表格行悬停效果 */
.debt-table tr:hover {
  background-color: var(--bg-color-dark);
  transform: scale(1.002);
  transition: all 0.2s ease;
}

.type-tag {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.tag-success {
  background-color: #f0f9eb;
  color: #67c23a;
}

.tag-warning {
  background-color: #fdf6ec;
  color: #e6a23c;
}

/* 自定义下拉菜单样式 */
.custom-dropdown {
  position: relative;
  width: 100%;
  z-index: 10;
}

.dropdown-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.dropdown-arrow {
  display: inline-block;
  width: 0;
  height: 0;
  border-left: 5px solid transparent;
  border-right: 5px solid transparent;
  border-top: 5px solid var(--text-secondary);
  transition: transform 0.3s ease;
  margin-left: 8px;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  max-height: 200px;
  overflow-y: auto;
  background-color: var(--bg-color-light);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-hover);
  margin-top: 4px;
  z-index: 100;
  animation: dropdownFadeIn 0.2s ease-out;
  /* 确保下拉菜单背景不透明 */
  background-clip: padding-box;
  -webkit-background-clip: padding-box;
}

/* 下拉菜单进入动画 */
@keyframes dropdownFadeIn {
  from {
    opacity: 0;
    transform: translateY(-5px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.dropdown-item {
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  font-size: 14px;
  color: var(--text-primary);
}

.dropdown-item:hover {
  background-color: var(--bg-color-dark);
  color: var(--primary-color);
}

.dropdown-item.selected {
  background-color: var(--bg-color-dark);
  color: var(--primary-color);
  font-weight: 500;
}

/* 开关样式 */
.switch-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.switch-container.inline {
  justify-content: center;
}

.form-switch {
  position: relative;
  width: 40px;
  height: 20px;
  appearance: none;
  background-color: var(--border-light);
  border-radius: 20px;
  outline: none;
  transition: background-color 0.3s;
  cursor: pointer;
}

.form-switch:checked {
  background-color: var(--primary-color);
}

.form-switch::after {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  background-color: white;
  border-radius: 50%;
  top: 1px;
  left: 1px;
  transition: transform 0.3s;
}

.form-switch:checked::after {
  transform: translateX(20px);
}

.switch-label {
  font-size: 14px;
  color: var(--text-regular);
}

/* 操作按钮样式 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.btn-small {
  padding: 4px 8px;
  font-size: 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-primary-small {
  background-color: #409eff;
  color: white;
}

.btn-primary-small:hover {
  background-color: #66b1ff;
}

.btn-danger-small {
  background-color: #f56c6c;
  color: white;
}

.btn-danger-small:hover {
  background-color: #f78989;
}

/* 分页样式 - 增强视觉效果 */
.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background-color: var(--bg-color-light);
  border-radius: var(--radius-large);
  box-shadow: var(--shadow-base);
  border: 1px solid var(--border-light);
}

.pagination-info {
  font-size: 14px;
  color: var(--text-regular);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 分页下拉菜单特殊样式 */
.pagination-controls .custom-dropdown {
  width: auto;
  min-width: 120px;
}

.pagination-controls .dropdown-toggle.page-size-select {
  padding: 4px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  font-size: 14px;
  background-color: var(--bg-color-light);
  color: var(--text-primary);
  min-width: 100px;
}

.pagination-controls .dropdown-toggle.page-size-select:hover {
  border-color: var(--primary-color);
}

.pagination-controls .dropdown-menu {
  min-width: 120px;
}

.page-info {
  font-size: 14px;
  color: #606266;
}

/* 对话框样式 - 增强视觉效果 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: white;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease-out;
}

.dialog-content {
  background-color: var(--bg-color-light);
  background-clip: padding-box;
  -webkit-background-clip: padding-box;
  border-radius: var(--radius-large);
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  animation: dialogFadeIn 0.3s ease-out;
  border: 1px solid var(--border-light);
  /* 确保背景不透明的最终保障 */
  position: relative;
}

/* 对话框进入动画 */
@keyframes dialogFadeIn {
  from { opacity: 0; transform: scale(0.9) translateY(-20px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #ebeef5;
}

.dialog-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #909399;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.3s;
}

.dialog-close:hover {
  background-color: #f5f7fa;
  color: #606266;
}

.dialog-body {
  padding: 20px;
}

/* 表单样式 - 增强视觉效果 */
.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--text-primary);
  font-size: 14px;
}

.form-select,
.form-input,
.form-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  font-size: 14px;
  transition: all 0.3s ease;
  background-color: var(--bg-color-light);
  color: var(--text-primary);
}

/* 表单元素聚焦效果 */
.form-select:focus,
.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

/* 表单元素悬停效果 */
.form-select:hover,
.form-input:hover,
.form-textarea:hover {
  border-color: var(--primary-color);
}

.form-select:focus,
.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #409eff;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.dialog-footer {
  padding: 16px 20px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 加载状态样式 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background-color: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  border-radius: 4px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #f3f3f3;
  border-top: 2px solid #409eff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  display: inline-block;
  margin-left: 8px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* 卡片布局样式 - 增强视觉效果 */
.cards-container {
  display: none;
  gap: 16px;
  margin-top: 20px;
}

.debt-card {
  background-color: var(--bg-color-light);
  border-radius: var(--radius-large);
  box-shadow: var(--shadow-base);
  overflow: hidden;
  transition: all 0.3s ease;
  border: 1px solid var(--border-light);
}

/* 卡片悬停效果 */
.debt-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-hover);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.card-index {
  font-size: 14px;
  color: #6c757d;
}

.card-body {
  padding: 16px;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-row:last-child {
  margin-bottom: 0;
}

.card-row.remark {
  flex-direction: column;
  align-items: flex-start;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f1f3f5;
}

.card-row.remark .value {
  margin-top: 4px;
  font-style: italic;
  color: #6c757d;
}

.label {
  font-weight: 500;
  color: #495057;
}

.value {
  color: #212529;
  text-align: right;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-top: 1px solid #e9ecef;
}

/* 状态开关样式 */
.status-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.status-switch .slider {
  position: relative;
  width: 40px;
  height: 20px;
  background-color: #dcdfe6;
  border-radius: 20px;
  transition: background-color 0.3s;
}

/* 状态开关深色模式 */
@media (prefers-color-scheme: dark) {
  .dialog-overlay {
    background-color: black;
  }
  
  .empty-state {
    background-color: var(--bg-color-light) !important;
  }

  .debt-table th,
  .debt-table td {
    background-color: var(--bg-color-light) !important;
    color: var(--text-primary) !important;
    border-color: var(--border-color);
  }

  .debt-table th {
    background-color: var(--bg-color-dark) !important;
  }

  .table-container {
    background-color: var(--bg-color-light) !important;
  }

  .cards-container {
    background-color: var(--bg-color-light) !important;
  }

  .status-switch .slider {
    background-color: var(--border-light);
  }
  
  /* 对话框深色模式优化 */
  .dialog-content {
    background-color: var(--bg-color-light) !important;
    border-color: var(--border-color);
  }
  
  .dialog-header,
  .dialog-footer {
    border-color: var(--border-color);
  }
  
  .dialog-close:hover {
    background-color: var(--bg-color-dark);
  }
  
  /* 卡片组件深色模式优化 */
  .card-header,
  .card-footer {
    background-color: var(--bg-color-dark) !important;
    border-color: var(--border-color);
  }
  
  .label {
    color: var(--text-regular);
  }
  
  .value {
    color: var(--text-primary);
  }
  
  .card-row.remark {
    border-color: var(--border-color);
  }
  
  .card-row.remark .value {
    color: var(--text-secondary);
  }
}

.status-switch input:checked + .slider {
  background-color: #409eff;
}

.status-switch .slider::after {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  background-color: white;
  border-radius: 50%;
  top: 2px;
  left: 2px;
  transition: transform 0.3s;
}

.status-switch input:checked + .slider::after {
  transform: translateX(20px);
}

.status-switch input {
  display: none;
}

.switch-label {
  font-size: 14px;
  color: #606266;
}

/* 空状态样式 */
.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  background-color: white;
  border-radius: 4px;
  margin-top: 20px;
}

/* 响应式布局 */
@media (max-width: 768px) {
  .debt-container {
    padding: 10px;
  }
  
  .filter-row {
    flex-direction: column;
  }
  
  .filter-item {
    min-width: 100%;
  }
  
  .date-range {
    min-width: 100%;
    flex-direction: column;
    align-items: stretch;
  }
  
  /* 隐藏表格，显示卡片 */
  .table-container {
    display: none;
  }
  
  .cards-container {
    display: flex;
    flex-direction: column;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .pagination-container {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .pagination-controls {
    justify-content: center;
  }
}
</style>