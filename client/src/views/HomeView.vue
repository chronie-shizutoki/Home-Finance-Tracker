<template>
  <!-- 全局强制捐款弹窗 -->
  <div v-if="showDonationModal" class="donation-modal-overlay">
    <div class="donation-modal-content">
      <h2 class="donation-modal-title">请支持我们的项目</h2>
      <p class="donation-modal-message">为了继续提供优质服务，请捐款至少30元。完成捐款后您可以继续使用应用。</p>
      <div class="donation-modal-footer">
        <el-button type="primary" @click="proceedToDonation" :disabled="donationAmount < 30">
          前往捐款
        </el-button>
      </div>
    </div>
  </div>

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
  <el-button type="primary" @click="showAiAddDialog = true" size="default">
    <el-icon><Cpu /></el-icon>
    AI智能记录
  </el-button>
  <el-button type="primary" @click="showAiReportDialog = true" size="default">
    <el-icon><Document /></el-icon>
    AI消费报告
  </el-button>
  <el-button type="success" @click="showTodoDialog = true" size="default">
    <el-icon><List /></el-icon>
    {{ t('todo.title') }}
  </el-button>
  <el-button type="info" @click="goToDonation" size="default">
    <el-icon><Money /></el-icon>
    {{ t('donation.title') }}
  </el-button>
  <el-button type="warning" @click="goToDebts" size="default">
    <el-icon><CreditCard /></el-icon>
    {{ t('debt.title') }}
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
    
    <!-- 图表分析按钮 -->
    <el-button type="primary" @click="goToCharts" size="default" style="margin-bottom: 20px;">
      <el-icon><PieChart /></el-icon>
      {{ t('chart.title') }}
    </el-button>
    
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

  <!-- API密钥设置对话框 -->
  <el-dialog v-model="showApiKeyDialog" title="设置SiliconFlow API密钥" width="50%">
    <el-form :model="apiKeyForm" ref="apiKeyFormRef">
      <el-form-item :label="'API密钥'" prop="apiKey">
        <el-input
          v-model="apiKeyForm.apiKey"
          placeholder="请输入您的SiliconFlow API密钥"
          type="password"
          show-password
        ></el-input>
        <div style="margin-top: 10px; font-size: 12px; color: #606266;">
          获取API密钥: <a href="https://console.siliconflow.cn/api-keys" target="_blank">https://console.siliconflow.cn/api-keys</a>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showApiKeyDialog = false">取消</el-button>
      <el-button type="primary" @click="handleApiKeySave">保存</el-button>
    </template>
  </el-dialog>

  <!-- AI智能记录对话框 -->
  <el-dialog v-model="showAiAddDialog" title="AI智能记录" width="80%">
    <el-form :model="aiForm" ref="aiFormRef">
      <el-form-item :label="'输入文本描述'">
        <el-input
          v-model="aiForm.text"
          type="textarea"
          :rows="4"
          placeholder="请输入消费记录的详细描述，例如：今天上午在超市买了苹果和牛奶，共花费56.8元。"
        ></el-input>
      </el-form-item>
      <el-form-item :label="'或上传图片'">
        <el-upload
          v-model:file-list="aiForm.image"
          class="avatar-uploader"
          action=""
          :auto-upload="false"
          :on-change="handleImageChange"
          :show-file-list="true"
          accept=".jpg,.jpeg,.png,.gif"
        >
          <el-button size="small" type="primary">点击上传</el-button>
          <template #tip>
            <div class="el-upload__tip">
              请上传包含消费信息的图片（如收据、账单截图等）
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleAiCancel">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleAiGenerate" :loading="isParsing">
        {{ isParsing ? '生成中...' : '生成记录' }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 多条记录编辑对话框 -->
  <el-dialog v-model="showMultiRecordsDialog" title="AI生成的多条记录" width="90%">
    <div v-if="multiRecords.length === 0" class="no-records">
      {{ t('expense.noRecords') }}
    </div>
    <div v-else class="multi-records-container">
      <!-- 全选功能 -->
      <div class="select-all-container" style="margin-bottom: 20px;">
        <el-checkbox v-model="selectAll" @change="handleSelectAllChange">{{ t('common.selectAll') }}</el-checkbox>
      </div>
      
      <!-- 记录列表 -->
      <div v-for="(record, index) in multiRecords" :key="index" class="record-item" style="margin-bottom: 15px; padding: 15px; border: 1px solid #e4e7ed; border-radius: 4px;">
        <div style="display: flex; align-items: center; margin-bottom: 10px;">
          <el-checkbox v-model="record.selected" @change="handleRecordSelectChange"></el-checkbox>
          <span style="margin-left: 10px; font-weight: 500;">{{ t('expense.record') }} {{ index + 1 }}</span>
        </div>
        <div style="display: flex; flex-wrap: wrap; gap: 15px;">
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.type') }}:</label>
            <el-select v-model="record.type" :placeholder="t('expense.selectType')" style="width: 100%;">
              <el-option v-for="type in expenseTypes" :key="type" :label="type" :value="type"></el-option>
            </el-select>
          </div>
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.amount') }}:</label>
            <el-input v-model="record.amount" :placeholder="0" type="text" style="width: 100%;" />
          </div>
          <div style="flex: 1; min-width: 200px;">
            <label style="display: block; margin-bottom: 5px;">{{ t('expense.date') }}:</label>
            <input type="date" v-model="record.date" :placeholder="t('expense.selectDate')" class="el-input__inner" style="width: 100%;">
          </div>
        </div>
        <div style="margin-top: 15px;">
          <label style="display: block; margin-bottom: 5px;">{{ t('expense.remark') }}:</label>
          <el-input v-model="record.remark" :placeholder="t('expense.enterRemark')" type="textarea" :rows="2" style="width: 100%;"></el-input>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleMultiRecordsCancel">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleMultiRecordsSubmit">{{ t('common.submit') }} ({{ selectedRecordsCount }}/{{ multiRecords.length }})</el-button>
    </template>
  </el-dialog>

  <!-- AI消费报告对话框 -->
  <el-dialog v-model="showAiReportDialog" title="AI消费报告" width="90%" height="80vh">
    <div class="ai-report-container">
      <!-- 问题输入区域 -->
      <div class="report-question-section" style="margin-bottom: 20px;">
        <el-form label-position="top">
          <el-form-item label="输入您的问题（可选）">
            <el-input
              v-model="reportQuestion"
              type="textarea"
              :rows="3"
              placeholder="您可以向AI提问关于您的消费情况，例如：'我本月的主要消费类别是什么？'或'如何减少我的日常开支？'"
            />
            <div style="margin-top: 10px; display: flex; gap: 10px;">
              <el-button type="primary" @click="handleGenerateReport" :loading="isGeneratingReport">
                {{ isGeneratingReport ? '生成中...' : '生成报告' }}
              </el-button>
              <el-button @click="clearReportQuestion">清空问题</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 报告内容显示区域 -->
      <div class="report-content-section">
        <div v-if="!reportContent" class="no-report-content">
          请点击"生成报告"按钮开始分析您的消费数据
        </div>
        <div v-else class="report-content" v-html="renderedReportContent" style="white-space: pre-wrap;">
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="showAiReportDialog = false">关闭</el-button>
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
import { Plus, Document, List, Box, Refresh, Upload, Money, CreditCard, Cpu, PieChart } from '@element-plus/icons-vue';
import axios from 'axios';
import { ref, computed, onMounted, onBeforeUnmount, reactive, defineAsyncComponent, watch } from 'vue';
import { marked } from 'marked';

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
const showAiAddDialog = ref(false);
// 新增：显示多条记录的对话框
const showMultiRecordsDialog = ref(false);
// 新增：显示AI报告对话框
const showAiReportDialog = ref(false);
const aiForm = reactive({
  text: '',
  image: []
});
const isParsing = ref(false);
// 新增：存储多条记录的数据结构
const multiRecords = ref([]);
// 新增：全选状态
const selectAll = ref(false);
// 新增：报告相关状态
const isGeneratingReport = ref(false);
const reportContent = ref('');
const reportQuestion = ref('');

// 配置marked选项
marked.setOptions({
  breaks: true,
  gfm: true
});

// 渲染报告内容为HTML
const renderedReportContent = computed(() => {
  return marked.parse(reportContent.value);
});

// 新增：计算已选择的记录数量
const selectedRecordsCount = computed(() => {
  return multiRecords.value.filter(record => record.selected).length;
});

// 导入AI API
import { parseTextToRecord, parseImageToRecord, setApiKey, generateExpenseReport } from '@/api/aiRecord';

// API密钥相关
const showApiKeyDialog = ref(false);
const apiKeyForm = reactive({
  apiKey: localStorage.getItem('siliconflow_api_key') || ''
});

// 全局强制捐款弹窗状态
// 检查是否在当前会话中已完成捐款
const hasCompletedCurrentSessionDonation = sessionStorage.getItem('hasCompletedCurrentSessionDonation');
// 如果没有完成捐款，则显示弹窗
const showDonationModal = ref(!hasCompletedCurrentSessionDonation); 
const donationAmount = ref(30); // 默认捐款金额为30元
let donationStatusTimer = null;

// 前往债务管理页面
const goToDebts = () => {
  router.push('/debts');
};

// 前往图表页面
const goToCharts = () => {
  router.push('/charts');
};

// 前往捐款页面
const proceedToDonation = () => {
  if (donationAmount.value >= 30) {
    // 保存用户选择的捐款金额到localStorage
    localStorage.setItem('donationAmount', donationAmount.value.toString());
    // 保存当前页面作为重定向目标
    const currentPath = window.location.pathname + window.location.search;
    localStorage.setItem('redirectAfterDonation', currentPath);
    // 跳转到捐款页面
    router.push('/donation');
  }
};

// 防止用户通过ESC键关闭弹窗
const preventEscClose = (e) => {
  if (e.key === 'Escape' && showDonationModal.value) {
    e.preventDefault();
    e.stopPropagation();
  }
};

// 防止用户右键菜单
const preventContextMenu = (e) => {
  if (showDonationModal.value) {
    e.preventDefault();
    e.stopPropagation();
  }
};

// 定期检查捐款状态
const checkDonationStatus = () => {
  // 清除之前可能存在的定时器
  if (donationStatusTimer) {
    clearInterval(donationStatusTimer);
  }
  
  // 每2秒检查一次捐款状态
  donationStatusTimer = setInterval(() => {
    const hasCompletedCurrentSessionDonation = sessionStorage.getItem('hasCompletedCurrentSessionDonation');
    showDonationModal.value = !hasCompletedCurrentSessionDonation;
  }, 2000);
};

// 监听路由变化，确保用户无法绕过捐款
const handleRouteChange = () => {
  if (showDonationModal.value) {
    // 除非用户在捐款页面，否则强制显示弹窗
    const currentPath = window.location.pathname;
    if (currentPath !== '/donation') {
      showDonationModal.value = true;
    }
  }
};



// 组件卸载时清理事件监听器
onBeforeUnmount(() => {
  if (dateTimeTimer) {
    clearInterval(dateTimeTimer);
  }
  
  if (donationStatusTimer) {
    clearInterval(donationStatusTimer);
  }
  
  document.removeEventListener('keydown', preventEscClose);
  document.removeEventListener('contextmenu', preventContextMenu);
  window.removeEventListener('popstate', handleRouteChange);
});

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
onMounted(async () => {
  loadMarkdownReport();
  // 初始化并启动日期时间更新
  updateDateTime();
  dateTimeTimer = setInterval(updateDateTime, 1000);
  
  // 添加新的事件监听器以强制捐款弹窗
  document.addEventListener('keydown', preventEscClose);
  document.addEventListener('contextmenu', preventContextMenu);
  
  // 启动捐款状态检查机制
  checkDonationStatus();
  
  // 监听路由变化
  window.addEventListener('popstate', handleRouteChange);
  
  try {
    await fetchData(false);
  } catch (err) {
    console.error('Failed to initialize data:', err);
    error.value = t('error.dataInitializationFailed');
  }
});
watch(locale, loadMarkdownReport);

// 清理定时器和事件监听器
onBeforeUnmount(() => {
  if (dateTimeTimer) {
    clearInterval(dateTimeTimer);
  }
  
  // 移除强制捐款弹窗相关的事件监听器
  document.removeEventListener('keydown', preventEscClose);
  document.removeEventListener('contextmenu', preventContextMenu);
  window.removeEventListener('popstate', handleRouteChange);
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

// 处理AI智能记录生成
const aiFormRef = ref(null);

const handleImageChange = (file, fileList) => {
  aiForm.image = fileList;
};

// 处理AI智能记录取消
const handleAiCancel = () => {
  showAiAddDialog.value = false;
  // 重置AI表单
  aiForm.text = '';
  aiForm.image = [];
};

// 新增：处理全选/取消全选
const handleSelectAllChange = (value) => {
  multiRecords.value.forEach(record => {
    record.selected = value;
  });
};

// 新增：处理单个记录选择变化
const handleRecordSelectChange = () => {
  const allSelected = multiRecords.value.every(record => record.selected);
  const noneSelected = multiRecords.value.every(record => !record.selected);
  
  selectAll.value = allSelected;
  // 处理半选中状态
  if (!allSelected && !noneSelected) {
    selectAll.value = undefined;
  }
};

// 新增：处理多条记录对话框取消
const handleMultiRecordsCancel = () => {
  showMultiRecordsDialog.value = false;
  multiRecords.value = [];
  selectAll.value = false;
};

// 新增：处理多条记录提交
const handleMultiRecordsSubmit = async () => {
  try {
    // 获取所有选中的记录
    const selectedRecords = multiRecords.value.filter(record => record.selected);
    
    if (selectedRecords.length === 0) {
      ElMessage.warning('请至少选择一条记录');
      return;
    }
    
    // 验证并格式化所有记录
    const validRecords = [];
    for (const record of selectedRecords) {
      // 验证金额
      if (!record.amount || isNaN(record.amount) || Number(record.amount) <= 0) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的金额无效`);
      }
      
      // 验证类型
      if (!record.type) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的类型不能为空`);
      }
      
      // 验证日期
      if (!record.date) {
        throw new Error(`第${multiRecords.value.indexOf(record) + 1}条记录的日期不能为空`);
      }
      
      // 格式化记录
      validRecords.push({
        type: record.type,
        amount: parseFloat(parseFloat(record.amount).toFixed(2)),
        remark: record.remark || '',
        time: record.date // 服务器需要的时间字段
      });
    }
    
    // 提交所有记录
    for (const record of validRecords) {
      await axios.post('/api/expenses', record, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
    }
    
    // 关闭对话框
    showMultiRecordsDialog.value = false;
    
    // 刷新数据
    await fetchData(true);
    
    ElMessage.success(`${validRecords.length}条记录添加成功`);
    
    // 重置数据
    multiRecords.value = [];
    selectAll.value = false;
  } catch (error) {
    console.error('批量添加记录失败:', error);
    ElMessage.error(`添加记录失败: ${error.message}`);
  }
};

const handleAiGenerate = async () => {
  try {
    // 检查API密钥
    if (!checkApiKey()) {
      return;
    }
    
    // 检查是否有输入
    if (!aiForm.text && (!aiForm.image || aiForm.image.length === 0)) {
      ElMessage.error('请输入文本描述或上传图片');
      return;
    }

    isParsing.value = true;
    let parsedDataList;

    // 解析文本或图片
    if (aiForm.text) {
      parsedDataList = await parseTextToRecord(aiForm.text);
    } else if (aiForm.image && aiForm.image.length > 0) {
      parsedDataList = await parseImageToRecord(aiForm.image[0].raw);
    }

    // 处理解析结果
    if (parsedDataList && parsedDataList.length > 0) {
      if (parsedDataList.length === 1) {
        // 只有一条记录，保持原有逻辑
        const parsedData = parsedDataList[0];
        form.type = parsedData.type || '';
        form.amount = parsedData.amount || '';
        form.date = parsedData.date || '';
        form.remark = parsedData.remark || '';

        // 关闭AI对话框，打开普通编辑对话框
        showAiAddDialog.value = false;
        showAddDialog.value = true;
        ElMessage.success('AI已成功生成记录，请检查并确认');
      } else {
        // 多条记录，显示多条记录对话框
        multiRecords.value = parsedDataList.map(record => ({
          ...record,
          date: record.date || '',
          amount: record.amount || ''
        }));
        showAiAddDialog.value = false;
        showMultiRecordsDialog.value = true;
        ElMessage.success(`AI已成功生成${parsedDataList.length}条记录，请检查并确认`);
      }
    }
  } catch (error) {
    console.error('AI生成记录失败:', error);
    ElMessage.error('AI生成记录失败，请重试');
  } finally {
    isParsing.value = false;
    // 重置AI表单
    aiForm.text = '';
    aiForm.image = [];
  }
};

// 处理AI报告生成
const handleGenerateReport = async () => {
  try {
    // 检查API密钥
    if (!checkApiKey()) {
      return;
    }
    
    // 检查是否有消费数据
    if (!csvExpenses || csvExpenses.length === 0) {
      ElMessage.error('没有足够的消费数据来生成报告');
      return;
    }

    isGeneratingReport.value = true;
    reportContent.value = '';
    
    // 生成报告
    const content = await generateExpenseReport(csvExpenses.value, reportQuestion.value);
    reportContent.value = content;
    
    ElMessage.success('AI已成功生成消费报告');
  } catch (error) {
    console.error('AI生成报告失败:', error);
    ElMessage.error('AI生成报告失败，请重试');
  } finally {
    isGeneratingReport.value = false;
  }
};

// 清空报告问题
const clearReportQuestion = () => {
  reportQuestion.value = '';
};

// 处理API密钥设置
const handleApiKeySave = () => {
  if (apiKeyForm.apiKey) {
    localStorage.setItem('siliconflow_api_key', apiKeyForm.apiKey);
    setApiKey(apiKeyForm.apiKey);
    showApiKeyDialog.value = false;
    ElMessage.success('API密钥已保存');
  } else {
    ElMessage.error('请输入有效的API密钥');
  }
};

// 检查是否已设置API密钥
const checkApiKey = () => {
  const savedApiKey = localStorage.getItem('siliconflow_api_key');
  if (!savedApiKey) {
    ElMessage.warning('请先设置SiliconFlow API密钥');
    showApiKeyDialog.value = true;
    return false;
  }
  setApiKey(savedApiKey);
  return true;
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
  --donation-modal-overlay: rgba(0, 0, 0, 0.8);
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

/* 全局强制捐款弹窗样式 */
.donation-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: #000000;
  opacity: 0.95;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999999; /* 确保在最顶层 */
  overflow: hidden;
}

.donation-modal-content {
  background: var(--popup-content-bg);
  padding: 2.5rem;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  text-align: center;
  animation: donationModalAppear 0.5s ease-out;
}

@keyframes donationModalAppear {
  from {
    opacity: 0;
    transform: translateY(-50px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.donation-modal-title {
  font-size: 1.8rem;
  color: white;
  margin-bottom: 1.5rem;
  font-weight: 600;
}

.donation-modal-message {
  font-size: 1.1rem;
  color: white;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.donation-amount-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-bottom: 2rem;
}

.donation-amount-input {
  width: 120px;
  padding: 0.75rem 1rem;
  font-size: 1.2rem;
  border: 2px solid var(--border-primary);
  border-radius: 6px;
  text-align: center;
  transition: all 0.3s ease;
}

.donation-amount-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.2);
}

.donation-currency {
  font-size: 1.2rem;
  color: white;
}

.donation-modal-footer {
  display: flex;
  justify-content: center;
}

.donation-modal-footer .el-button {
  padding: 0.75rem 2rem;
  font-size: 1.1rem;
  border-radius: 8px;
}

/* 阻止背景滚动 */
body.donation-modal-open {
  overflow: hidden;
}

/* AI报告内容的Markdown样式 */
.report-content {
  line-height: 1.6;
  padding: 10px 0;
}

.report-content h1,
.report-content h2,
.report-content h3,
.report-content h4,
.report-content h5,
.report-content h6 {
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  font-weight: 600;
  color: var(--text-primary);
}

.report-content h1 {
  font-size: 1.8em;
  border-bottom: 1px solid #eee;
  padding-bottom: 0.3em;
}

.report-content h2 {
  font-size: 1.5em;
}

.report-content h3 {
  font-size: 1.2em;
}

.report-content p {
  margin-bottom: 1em;
  color: var(--text-primary);
}

.report-content ul,
.report-content ol {
  margin-left: 2em;
  margin-bottom: 1em;
  color: var(--text-primary);
}

.report-content li {
  margin-bottom: 0.5em;
}

.report-content strong {
  font-weight: 600;
}

.report-content em {
  font-style: italic;
}

.report-content code {
  background-color: #f5f5f5;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.9em;
}

.report-content pre {
  background-color: #f5f5f5;
  padding: 1em;
  border-radius: 4px;
  overflow-x: auto;
  margin-bottom: 1em;
  font-family: 'Courier New', Courier, monospace;
}

.report-content pre code {
  background-color: transparent;
  padding: 0;
}

.report-content blockquote {
  border-left: 4px solid #ddd;
  padding-left: 1em;
  color: #666;
  margin-left: 0;
  margin-right: 0;
  margin-bottom: 1em;
}

.report-content table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 1em;
}

.report-content th,
.report-content td {
  padding: 8px 12px;
  border: 1px solid #ddd;
}

.report-content th {
  background-color: #f9f9f9;
  font-weight: 600;
}

.report-content tr:nth-child(even) {
  background-color: #f9f9f9;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .report-content h1,
  .report-content h2,
  .report-content h3,
  .report-content p,
  .report-content ul,
  .report-content ol {
    color: #e5e7eb;
  }
  
  .report-content h1 {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
  
  .report-content code,
  .report-content pre {
    background-color: rgba(255, 255, 255, 0.1);
  }
  
  .report-content blockquote {
    border-left-color: rgba(255, 255, 255, 0.2);
    color: #9ca3af;
  }
  
  .report-content th {
    background-color: rgba(255, 255, 255, 0.05);
  }
  
  .report-content tr:nth-child(even) {
    background-color: rgba(255, 255, 255, 0.05);
  }
  
  .report-content th,
  .report-content td {
    border-color: rgba(255, 255, 255, 0.1);
  }
}
</style>
