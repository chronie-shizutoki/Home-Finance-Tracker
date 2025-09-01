<template>
  <div class="container">
    <!-- 主弹窗 -->
    <div v-if="isLoadingCsv" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <Header :title="$t('app.title')" />
    <div style="display: flex; flex-wrap: wrap; gap: 10px;">
    <el-button type="primary" @click="showAddDialog = true" size="default">
    <el-icon><Plus /></el-icon>
    {{ t('expense.addRecord') }}
  </el-button>
  <el-button type="primary" @click="showMarkdownDialog = true" size="default">
    <el-icon><Document /></el-icon>
    {{ t('home.viewDocument') }}
  </el-button>
  <el-button type="success" @click="showTodoDialog = true" size="default">
    <el-icon><List /></el-icon>
    {{ t('todo.title') }}
  </el-button>
  <el-button type="info" @click="goToDonation" size="default">
    <el-icon><Money /></el-icon>
    {{ t('donation.title') }}
  </el-button>
  <el-upload
    class="upload-excel"
    action="/api/import/excel"
    :show-file-list="false"
    :on-success="handleImportSuccess"
    :on-error="handleImportError"
    accept=".xlsx, .xls"
  >
    <el-button type="warning" size="default">
      <el-icon><Upload /></el-icon>
      {{ t('import.title') }}
    </el-button>
  </el-upload>
  </div>
<!-- 当前日期时间显示 -->
<div class="current-datetime" style="display: flex; justify-content: center;">{{ currentDateTime }}</div>

    <!-- 月度消费限制显示 -->
    <SpendingLimitDisplay :expenses="csvExpenses" />
    
    <!-- 支出图表分析 -->
    <ExpenseCharts :expenses="csvExpenses" />
    
    <ExpenseList :expenses="csvExpenses" />
    <div :class="['header']"></div>
    <Transition name="button">
      <ExportButton
        v-if="csvExpenses.length > 0"
        @export-excel="() => exportToExcel(csvExpenses)"
      />
      <div v-else class="no-data">{{ t('home.noDataForExport') }}</div>
    </Transition>
  </div>

  <!-- 悬浮刷新按钮 -->
  <div class="floating-refresh-btn">
    <el-button type="primary" icon="Refresh" @click="refreshPage()" size="default" circle />
  </div>

  <!-- 添加记录对话框 -->
  <el-dialog v-model="showAddDialog" :title="t('expense.addDialogTitle')" width="80%">
    <el-form :model="form" :rules="rules" ref="formRef">
      <el-form-item :label="t('expense.type')" prop="type">
        <el-select v-model="form.type" :placeholder="t('expense.selectType')">
          <el-option v-for="type in expenseTypes" :key="type" :label="type" :value="type"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item :label="t('expense.amount')" prop="amount">
        <el-input v-model="form.amount" :placeholder="0" type="text" />
      </el-form-item>
      <el-form-item :label="t('expense.date')" prop="date">
        <div class="el-input">
          <input type="date" v-model="form.date" :placeholder="t('expense.selectDate')" class="el-input__inner" style="width: 100%;">
        </div>
      </el-form-item>
      <el-form-item :label="t('expense.remark')">
        <el-input v-model="form.remark" :placeholder="t('expense.enterRemark')"></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showAddDialog = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleAddRecord">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>

    <MarkdownDialog
      v-model:visible="showMarkdownDialog"
      :title="markdownTitle"
      :content="markdownContent"
    />

    <!-- 待办事项对话框 -->
    <el-dialog v-model="showTodoDialog" :title="t('todo.title')" width="90%" top="5vh">
      <TodoList />
    </el-dialog>

</template>

<script setup>
import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElIcon, ElMessage, ElUpload } from 'element-plus';
import { Plus, Document, List, Box, Refresh, Upload, Money } from '@element-plus/icons-vue';
import axios from 'axios';
import { ref, computed, onMounted, onBeforeUnmount, reactive, defineAsyncComponent, watch } from 'vue';

import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import Papa from 'papaparse';

import { useExpenseData } from '@/composables/useExpenseData';
import { useExcelExport } from '@/composables/useExcelExport';

const MessageTip = defineAsyncComponent(() => import('@/components/MessageTip.vue'));
const Header = defineAsyncComponent(() => import('@/components/Header.vue'));
const ExpenseList = defineAsyncComponent(() => import('@/components/ExpenseList.vue'));
const ExpenseCharts = defineAsyncComponent(() => import('@/components/ExpenseCharts.vue'));
const ExportButton = defineAsyncComponent(() => import('@/components/ExportButton.vue'));
const MarkdownDialog = defineAsyncComponent(() => import('@/components/MarkdownDialog.vue'));
const TodoList = defineAsyncComponent(() => import('@/components/TodoList.vue'));
const SpendingLimitDisplay = defineAsyncComponent(() => import('@/components/SpendingLimitDisplay.vue'));

const { t, locale } = useI18n();
const router = useRouter();

// 按钮状态变量
const showAddDialog = ref(false);
const showMarkdownDialog = ref(false);
const showTodoDialog = ref(false);

// 导入处理
const handleImportSuccess = () => {
  ElMessage.success(t('import.success'));
};

const handleImportError = (error) => {
  ElMessage.error(t('import.failed'));
  console.error('Import error:', error);
};

// 跳转到捐赠页面
const goToDonation = () => {
  router.push('/donation');
};
const markdownContent = ref('');
const markdownTitle = ref('');
// 当前日期时间状态
const currentDateTime = ref('');
let dateTimeTimer = null;

// 根据当前语言加载对应的Markdown报告
const loadMarkdownReport = async () => {
  try {
    // 构建语言对应的文件名
    const lang = locale.value || 'en-US';
    // 动态导入对应语言的Markdown文件（明确指定.md扩展名）
    const module = await import(`@/assets/markdown/expense-report.${lang}.md?raw`);
    const content = module.default;
    markdownContent.value = content;

    // 提取标题（第一行内容，移除#和空格）
    const lines = content.split('\n');
    if (lines.length > 0) {
      markdownTitle.value = lines[0].replace(/^#+\s*/, '').trim();
    }
  } catch (error) {
    console.error('加载Markdown报告失败:', error);
    //  fallback to English version if current language not available
    try {
      const module = await import('@/assets/markdown/expense-report.en-US.md?raw');
      markdownContent.value = module.default;
      const lines = module.default.split('\n');
      markdownTitle.value = lines[0].replace(/^#+\s*/, '').trim();
    } catch (fallbackError) {
      console.error('加载默认Markdown报告失败:', fallbackError);
      markdownContent.value = '# 消费报告加载失败\n\n无法加载当前语言的消费报告，请检查文件是否存在。';
      markdownTitle.value = '报告加载失败';
    }
  }
};

// 初始加载和语言变化时重新加载
onMounted(() => {
  loadMarkdownReport();
  // 初始化并启动日期时间更新
  updateDateTime();
  dateTimeTimer = setInterval(updateDateTime, 1000);
});
watch(locale, loadMarkdownReport);

// 清理定时器
onBeforeUnmount(() => {
  if (dateTimeTimer) {
    clearInterval(dateTimeTimer);
  }
});

// 更新日期时间函数
const updateDateTime = () => {
  const now = new Date();
  // 根据当前语言环境和设备时区格式化日期时间
  const options = {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    weekday: 'long',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  };
  currentDateTime.value = now.toLocaleString(locale.value, options);
};

// 对话框相关数据
const expenseTypes = ['日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品', '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他', '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气'];
const form = reactive({
  type: '',
  amount: '',
  date: '',
  remark: ''
});

// 表单验证规则
const rules = {
  type: [{ required: true, message: t('expense.selectType'), trigger: 'change' }],
  amount: [
    { required: true, message: t('expense.inputAmount'), trigger: 'blur' },
    { required: true, message: t('expense.amountRequired'), trigger: 'blur' }
  ],
  date: [{ required: true, message: t('expense.selectDate'), trigger: 'change' }]
};

// 表单引用
const formRef = ref(null);

const handleAddRecord = async () => {
  try {
    // 验证表单
    await formRef.value.validate();

    // 验证金额为正数且支持两位小数
    // 检查金额是否存在且为有效数字
    if (form.amount === undefined || form.amount === null) {
      throw new Error(t('expense.amountUndefined'));
    }
    // 检查金额是否存在且为有效数字
    if (form.amount === undefined || form.amount === null) {
      throw new Error(t('expense.amountUndefined'));
    }
    // 检查金额是否存在且为有效数字
    if (form.amount === undefined || form.amount === null) {
      throw new Error(t('expense.amountUndefined'));
    }
    // 处理可能的undefined/null值并转换为字符串
    const amountStr = form.amount.toString().replace(',', '.');
    const amount = Number(amountStr);
    if (isNaN(amount) || amount <= 0 || !/^\d+(\.\d{1,2})?$/.test(amountStr)) {
      throw new Error(t('expense.invalidAmountFormat'));
    }

    // 格式化日期为YYYY-MM-DD格式
    const formattedDate = form.date ? new Date(form.date).toISOString().split('T')[0] : '';

    // 构建符合API要求的请求数据
    const expenseData = {
      type: form.type,
      amount: parseFloat(parseFloat(form.amount).toFixed(2)),
      remark: form.remark,
      time: formattedDate // 服务器需要的时间字段
    };

    // 使用与Expenses.vue相同的批量提交接口，明确指定为1条记录
    await axios.post('/api/expenses', expenseData, {
      headers: {
        'Content-Type': 'application/json'
      }
    });
    showAddDialog.value = false;
    // 添加成功后刷新数据
    await fetchData(true);
    ElMessage.success(t('expense.addSuccess'));
    // 重置表单
    Object.assign(form, { type: '', amount: '', date: '', remark: '' });
  } catch (error) {
    console.error('添加记录失败:', error);
    console.error('错误详情:', { status: error.response?.status, data: error.response?.data, headers: error.response?.headers });
    // 区分表单验证错误和API错误
    // 细化错误处理
    // 细化错误类型处理
    let errorMsg;
    if (error.name === 'ValidationError') {
      errorMsg = error.message;
    } else if (error.response) {
      // 服务器响应错误
      const status = error.response.status;
      const serverMsg = error.response.data?.message || '服务器处理异常';
      if (status >= 500) {
        errorMsg = t('expense.serverError', { error: serverMsg });
      } else if (status === 400) {
        errorMsg = t('expense.badRequest', { error: serverMsg });
      } else {
        errorMsg = t('expense.networkError', { error: `${status} - ${serverMsg}` });
      }
    } else if (error.request) {
      // 无响应错误（网络问题）
      errorMsg = t('expense.networkTimeout');
    } else {
      errorMsg = t('expense.unknownError', { error: error.message || '未知错误' });
    }
    ElMessage.error(errorMsg);
  }
};

// 状态数据
const csvExpenses = ref([]);
const isLoadingCsv = ref(false);

// 导出功能
const { exportToExcel } = useExcelExport();

// 费用数据管理
const {
  fetchData: originalFetchData,
  errorMessage,
  error,
  successMessage
} = useExpenseData();

// 封装 fetchData
const fetchData = async (forceRefresh = false) => {
  console.log('fetchData called, forceRefresh:', forceRefresh);
  await originalFetchData(forceRefresh);
  await loadCsvExpenses();
};

// 载入消费数据（从SQLite数据库）
const loadCsvExpenses = async () => {
  if (isLoadingCsv.value) return;
  isLoadingCsv.value = true;

  try {
    // 使用新的API端点获取所有数据
    const res = await axios.get(`/api/expenses?limit=10000`);
    
    let parsedData = [];
    
    // 适配新的API响应格式
    if (res.data && res.data.data && Array.isArray(res.data.data)) {
      // 新格式：{ data: [...], total: number, page: number, limit: number }
      parsedData = res.data.data;
    } else if (Array.isArray(res.data)) {
      // 兼容旧格式：直接返回数组
      parsedData = res.data;
    }

    // 确保数据格式正确
    csvExpenses.value = parsedData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        time: item.time
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);

    if (csvExpenses.value.length === 0) {
      console.warn('loadCsvExpenses: No valid data found in API response');
    } else {
      console.log('loadCsvExpenses: Data loaded, count:', csvExpenses.value.length);
    }
  } catch (err) {
    const errorInfo = err.response
      ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
      : err.message;
    errorMessage.value = t('error.loadCsvFailed', { error: errorInfo });
    error.value = errorMessage.value;

    console.error('loadCsvExpenses: Error Details:', err);
    csvExpenses.value = [];
  } finally {
    isLoadingCsv.value = false;
  }
};

// Function to force the browser to re-fetch new frontend data
const refreshPage = () => {
  // Force a full reload to bypass cache and fetch fresh data
  // Add a timestamp parameter to ensure the cache is invalidated
  if (window.location.reload) {
    window.location.href = window.location.href.split('?')[0] + '?t=' + new Date().getTime();
    window.location.reload(true);
  }
}

// 生命周期
onMounted(async () => {
  try {
    await fetchData(false);
  } catch (err) {
    console.error('Failed to initialize data:', err);
    error.value = t('error.dataInitializationFailed');
  }
});

</script>

<style scoped>
/* 定义 CSS 变量 */
:root {
  /* 弹窗样式变量 */
  --popup-bg: rgba(0,0,0,0.5);
  --popup-content-bg: #fff;
  --popup-btn-bg: #4CAF50;
  --popup-btn-color: white;
  --text-primary: #333;
  --text-secondary: #666;
  --bg-primary: #fff;
  --border-primary: #e0e0e0;
  --primary-color: #4CAF50;
  --error-bg: #ffebee;
  --error-border: #ffcdd2;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1rem;
  color: var(--text-primary);
  background: transparent;
  transition: all 0.3s ease;
}

.error-alert {
  padding: 1rem;
  margin-bottom: 1rem;
  background: var(--error-bg);
  border: 1px solid var(--error-border);
  border-radius: 8px;
  color: #d32f2f;
}

.btn {
  padding: 0.5rem 1rem;
  border-radius: 8px;
  transition: all 0.2s ease;
  cursor: pointer; /* 添加手势 */
}

.prev-btn, .next-btn {
  background: var(--primary-color);
  color: white;
  border: none;
}

.chart-btn {
  background: rgba(76, 175, 80, 0.1);
  color: var(--text-primary);
  border: 1px solid var(--border-primary);
}

.chart-btn.active {
  background: var(--primary-color);
  color: white;
  border-color: transparent;
}

.no-data {
  text-align: center;
  color: var(--text-secondary);
  padding: 2rem;
  min-height: 120px;
}

@media (max-width: 768px) {
  .container {
    padding: 1rem;
  }

  .chart-controls {
    margin: 1.5rem 0;
  }

  .month-label {
    font-size: 1rem;
  }

  .btn {
    padding: 0.4rem 0.8rem;
    font-size: 0.9rem;
  }
}

/* 过渡动画 */
.chart-enter-active,
.chart-leave-active {
  transition: opacity 0.5s ease;
}

.chart-enter-from,
.chart-leave-to {
  opacity: 0;
}

.button-enter-active,
.button-leave-active {
  transition: opacity 0.5s ease;
}

.button-enter-from,
.button-leave-to {
  opacity: 0;
}
/* 输入框容器（修正选择器确保生效） */
.confirm-input-container {
  position: relative;
  margin: 1.5rem 0;
  width: 100%;
  max-width: 300px;
}

/* 输入框基础样式（添加组件作用域前缀） */
.confirm-input {
  width: 90%;
  padding: 12px 16px;
  font-size: 1rem;
  background: var(--bg-primary);
  border: 2px solid var(--border-primary);
  border-radius: 8px;
  outline: none;
  transition: all 0.3s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  color: var(--text-primary);
}

/* 输入框占位符样式 */
.confirm-input::placeholder {
  color: var(--text-secondary);
  font-weight: 400;
}

/* 悬停效果 */
.confirm-input:hover {
  border-color: #b0b0b0;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.08);
}

/* 聚焦效果 */
.confirm-input:focus {
  border-color: #4361ee;
  box-shadow:
    0 4px 12px rgba(67, 97, 238, 0.2),
    0 0 0 3px rgba(67, 97, 238, 0.15);
  transform: translateY(-1px);
}

/* 输入框标签动画 */
.confirm-input-label {
  position: absolute;
  top: 13px;
  left: 15px;
  color: #888;
  pointer-events: none;
  transition: all 0.3s ease;
  background: white;
  padding: 0 4px;
}

.confirm-input:focus + .input-label,
.confirm-input:not(:placeholder-shown) + .input-label {
  top: -8px;
  left: 10px;
  font-size: 0.8rem;
  color: #4361ee;
  font-weight: 600;
}

/* 错误状态 */
.confirm-input-error .custom-input {
  border-color: #f44336;
}

.confirm-input-error .input-label {
  color: #f44336;
}

/* 禁用状态 */
.confirm-input:disabled {
  background: #f8f8f8;
  cursor: not-allowed;
  opacity: 0.7;
}

/* 悬浮刷新按钮样式 */
.floating-refresh-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 1000;
}

.floating-refresh-btn .el-button {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.floating-refresh-btn .el-button:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}
</style>
