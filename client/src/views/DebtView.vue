<template>
  <div class="debt-container">
    <div class="header-actions">
      <h2>{{ t('debt.title') }}</h2>
      <button class="btn-primary" @click="showAddDialog">
        {{ t('debt.addNewRecord') }}
      </button>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-container">
      <div class="filter-row mb-4">
        <div class="filter-item">
          <select v-model="filterType" class="filter-select">
            <option value="">{{ t('debt.allTypes') }}</option>
            <option value="lend">{{ t('debt.lend') }}</option>
            <option value="borrow">{{ t('debt.borrow') }}</option>
          </select>
        </div>
        <div class="filter-item">
          <select v-model="filterRepaid" class="filter-select">
            <option value="">{{ t('debt.allStatus') }}</option>
            <option value="true">{{ t('debt.repaid') }}</option>
            <option value="false">{{ t('debt.unrepaid') }}</option>
          </select>
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
        <select v-model="pagination.pageSize" @change="handleSizeChange" class="page-size-select">
          <option value="10">10 {{ t('common.perPage') }}</option>
          <option value="20">20 {{ t('common.perPage') }}</option>
          <option value="50">50 {{ t('common.perPage') }}</option>
          <option value="100">100 {{ t('common.perPage') }}</option>
        </select>
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
      <div class="dialog-content">
        <div class="dialog-header">
          <h3>{{ editingDebt ? t('debt.editDialogTitle') : t('debt.addDialogTitle') }}</h3>
          <button class="dialog-close" @click="cancelDialog">&times;</button>
        </div>
        <div class="dialog-body">
        <div class="form-item">
          <label>{{ t('debt.type') }} *</label>
          <select v-model="debtForm.type" class="form-select">
            <option value="lend">{{ t('debt.lend') }}</option>
            <option value="borrow">{{ t('debt.borrow') }}</option>
          </select>
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
import { ref, reactive, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { DebtAPI } from '@/api';

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
    
    // 添加日期范围筛选（如果有）
    // 注意：实际API可能需要不同的参数名或格式
    
    const response = await DebtAPI.getDebts(params);
    debtsData.value = response;
    // 实际项目中，应该从API响应中获取总数
    total.value = response.length;
  } catch (error) {
    console.error('获取债务记录失败:', error);
    ElMessage.error(t('debt.fetchFailed'));
  } finally {
    loading.value = false;
  }
};

// 重置筛选条件
const resetFilters = () => {
  filterType.value = '';
  filterRepaid.value = '';
  filterPerson.value = '';
  dateRange.value = [];
  pagination.currentPage = 1;
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
/* 基础样式 - 浅色模式 */
.debt-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .debt-container {
    color: #e0e0e0;
  }

  .filter-container {
    background-color: #2a2a2a !important;
  }

  .debt-card {
    background-color: #2a2a2a !important;
   color: #fffdfd !important;
  }
  
  .debt-card .el-card__header {
    background-color: #2a2a2a !important;
  }
  
  .dialog-content {
    background-color: rgb(0, 0, 0) !important;
  }
  /* 按钮样式 - 深色模式 */
  .btn {
    background-color: #333;
    color: #e0e0e0;
    border: 1px solid #444;
  }
  
  .btn:hover {
    background-color: #444;
  }
  
  /* 筛选区域样式 - 深色模式 */
  .filter-container {
    background-color: #2a2a2a;
  }
  
  .filter-select,
  .filter-input,
  .date-input,
  .form-select,
  .form-input,
  .form-textarea {
    background-color: #2a2a2a;
    color: #e0e0e0;
    border-color: #444;
  }
  
  .filter-select:focus,
  .filter-input:focus,
  .date-input:focus,
  .form-select:focus,
  .form-input:focus,
  .form-textarea:focus {
    border-color: #409eff;
    background-color: #333;
  }
  
  .range-separator,
  .switch-label,
  .pagination-info,
  .page-info {
    color: #aaa;
  }
  
  /* 表格样式 - 深色模式 */
  .debt-table {
    background-color: #2a2a2a;
  }
  
  .debt-table th,
  .debt-table td {
    border-color: #444;
    color: #e0e0e0;
  }
  
  .debt-table th {
    background-color: #333;
    color: #e0e0e0;
  }
  
  .debt-table tr:hover {
    background-color: #333;
  }
  
  /* 类型标签 - 深色模式 */
  .tag-success {
    background-color: #1a362a;
    color: #67c23a;
  }
  
  .tag-warning {
    background-color: #3a301a;
    color: #e6a23c;
  }
  
  /* 开关样式 - 深色模式 */
  .form-switch {
    background-color: #555;
  }
  
  /* 分页样式 - 深色模式 */
  .page-size-select {
    background-color: #2a2a2a;
    color: #e0e0e0;
    border-color: #444;
  }
  
  /* 对话框样式 - 深色模式 */
  .dialog-content {
    background-color: #2a2a2a;
  }
  
  .dialog-header,
  .dialog-footer {
    border-color: #444;
  }
  
  .dialog-header h3 {
    color: #e0e0e0;
  }
  
  .dialog-close {
    color: #aaa;
  }
  
  .dialog-close:hover {
    background-color: #333;
    color: #e0e0e0;
  }
  
  .form-item label {
    color: #e0e0e0;
  }
  
  /* 加载状态样式 - 深色模式 */
  .loading-container {
    background-color: #2a2a2a;
  }
  
  /* 卡片布局样式 - 深色模式 */
  .debt-card {
    background-color: #2a2a2a;
  }
  
  .card-header,
  .card-footer {
    background-color: #333 !important;
    border-color: #444;
  }
  
  .card-index {
    color: #aaa;
  }
  
  .card-row.remark {
    border-color: #444;
  }
  
  .card-row.remark .value {
    color: #aaa;
  }
  
  .label {
    color: #e0e0e0;
  }
  
  .value {
    color: #f0f0f0;
  }
  
  /* 空状态样式 - 深色模式 */
  .empty-state {
    color: #aaa;
    background-color: #2a2a2a;
  }

.form-item label {
  color: #fdfdfd !important;
}

}

/* 基础样式 - 继续 */

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

/* 按钮样式 */
.btn,
.btn-primary {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.btn {
  background-color: #f5f5f5;
  color: #606266;
}

.btn:hover {
  background-color: #e6e6e6;
}

.btn-primary {
  background-color: #409eff;
  color: white;
}

.btn-primary:hover {
  background-color: #66b1ff;
}

.btn-primary:disabled {
  background-color: #c0c4cc;
  cursor: not-allowed;
}

/* 筛选区域样式 */
.filter-container {
  background-color: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  margin-bottom: 20px;
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

.filter-select,
.filter-input,
.date-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.filter-select:focus,
.filter-input:focus,
.date-input:focus {
  outline: none;
  border-color: #409eff;
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

/* 表格样式 */
.debt-table {
  width: 100%;
  border-collapse: collapse;
  background-color: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.debt-table th,
.debt-table td {
  padding: 12px;
  border: 1px solid #ebeef5;
  text-align: left;
}

.debt-table th {
  font-weight: 600;
  color: #303133;
}

.debt-table tr:hover {
  background-color: #f5f7fa;
}

.type-tag {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.tag-success {
  background-color: #f0f9eb;
  color: #67c23a;
}

.tag-warning {
  background-color: #fdf6ec;
  color: #e6a23c;
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
  background-color: #dcdfe6;
  border-radius: 20px;
  outline: none;
  transition: background-color 0.3s;
  cursor: pointer;
}

.form-switch:checked {
  background-color: #409eff;
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
  color: #606266;
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

/* 分页样式 */
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-info {
  font-size: 14px;
  color: #606266;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-size-select {
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}

.page-info {
  font-size: 14px;
  color: #606266;
}

/* 对话框样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.dialog-content {
  background-color: white;
  border-radius: 4px;
  width: 90%;
  max-width: 600px;
  max-height: 90vh;
  overflow-y: auto;
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

/* 表单样式 */
.form-item {
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: #303133;
}

.form-select,
.form-input,
.form-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  transition: border-color 0.3s;
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

/* 卡片布局样式 */
.cards-container {
  display: none;
  gap: 16px;
  margin-top: 20px;
}

.debt-card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
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
  .empty-state {
    background-color: #20222a !important;
  }

.debt-table th,
.debt-table td {
  background-color: #20222a !important;
  color: #fff !important;
}

  .table-container {
    background-color: #20222a !important;
  }

  .cards-container {
    background-color: #20222a !important;
  }

  .status-switch .slider {
    background-color: #555;
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