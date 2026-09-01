<template>
  <!-- Header is OUTSIDE the .container (it has its own position:fixed placement
       and covers the top viewport strip). Keeping it separate removes any flex
       gap surprises from position-fixed siblings participating inside the flex
       container and eliminates the stats-bar/toolbar overlap. -->
  <Header :title="t('recycleBin.title')" :show-back="true" back-route="/" @back="onHeaderBack" />

  <div class="container">
    <!-- Message banners -->
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- Stats bar: total count + auto-purge hint -->
    <div class="stats-bar" v-if="!loading && totalCount > 0">
      <span class="record-count-hint">
        <FontAwesomeIcon icon="trash-alt" />
        {{ t('recycleBin.totalCount', { count: totalCount }) }}
        &nbsp;·&nbsp;
        <FontAwesomeIcon icon="hourglass-half" />
        {{ t('recycleBin.emptyHint') }}
      </span>
    </div>

    <!-- Toolbar -->
    <div class="toolbar" :class="{ 'select-mode-bar': selectMode }">
      <template v-if="!selectMode">
        <GlassButton type="secondary" @click="selectMode = true" :disabled="totalCount === 0 || loading">
          <FontAwesomeIcon icon="check-square" />
          {{ multiSelectLabel }}
        </GlassButton>
        <!-- Spacer that pushes the following action buttons to the right edge -->
        <div class="toolbar-spacer" aria-hidden="true"></div>
        <GlassButton type="primary" @click="handleRestoreAll" :disabled="totalCount === 0 || loading">
          <FontAwesomeIcon icon="undo" />
          {{ t('recycleBin.restoreAll') }}
        </GlassButton>
        <GlassButton type="warning" @click="handleEmptyBin" :disabled="totalCount === 0 || loading">
          <FontAwesomeIcon icon="exclamation-triangle" />
          {{ t('recycleBin.emptyBin') }}
        </GlassButton>
      </template>
      <template v-else>
        <div class="select-block">
          <label class="select-all-wrap" @click.prevent="toggleSelectAll">
            <input
              type="checkbox"
              class="select-all-checkbox"
              :checked="isAllSelected"
              :indeterminate.prop="isPartiallySelected"
              @change="toggleSelectAll"
            />
            <span class="select-caption">{{ selectCaption }}</span>
          </label>
          <span class="selected-count">
            {{ t('recycleBin.selectedCount', { count: selectedIds.size }) }}
          </span>
        </div>
        <div class="flex-grow" />
        <GlassButton type="secondary" @click="cancelSelection">
          {{ t('recycleBin.cancelSelection') }}
        </GlassButton>
        <GlassButton type="primary" @click="handleRestoreSelected" :disabled="selectedIds.size === 0">
          <FontAwesomeIcon icon="undo" />
          {{ t('recycleBin.restoreSelected') }}
        </GlassButton>
        <GlassButton type="warning" @click="handleDeleteSelected" :disabled="selectedIds.size === 0">
          <FontAwesomeIcon icon="trash" />
          {{ t('recycleBin.deleteSelected') }}
        </GlassButton>
      </template>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="loading-wrap">
      <div class="loading-spinner" />
      <p>{{ t('recycleBin.loading') }}</p>
    </div>

    <!-- Empty state -->
    <div v-else-if="totalCount === 0" class="empty-state">
      <div class="empty-icon">
        <FontAwesomeIcon icon="trash-alt" />
      </div>
      <h2>{{ t('recycleBin.empty') }}</h2>
      <p class="empty-hint">{{ t('recycleBin.emptyHint') }}</p>
      <GlassButton type="primary" size="large" @click="goBack">
        <FontAwesomeIcon icon="home" />
        {{ t('recycleBin.backToHome') }}
      </GlassButton>
    </div>

    <!-- Data lists (desktop + mobile, toggled via CSS media queries) -->
    <template v-else>
      <!-- Desktop table -->
      <div class="desktop-list">
      <div class="list-header row">
        <div class="col-check" v-if="selectMode">
          <input
            type="checkbox"
            class="select-all-checkbox"
            :checked="isAllSelected"
            :indeterminate.prop="isPartiallySelected"
            @change="toggleSelectAll"
          />
        </div>
        <div class="col-type">{{ t('expense.type') }}</div>
        <div class="col-amount">{{ t('expense.amount') }}</div>
        <div class="col-date">{{ t('expense.date') }}</div>
        <div class="col-deleted">{{ t('recycleBin.deletedAt') }}</div>
        <div class="col-actions">{{ t('common.action') }}</div>
      </div>

      <div
        v-for="item in sortedExpenses"
        :key="item.id"
        class="list-row row"
        :class="{ selected: selectedIds.has(item.id) }"
      >
        <div class="col-check" v-if="selectMode">
          <input
            type="checkbox"
            class="row-checkbox"
            :checked="selectedIds.has(item.id)"
            @change="toggleSelect(item.id)"
          />
        </div>
        <div class="col-type">
          <span class="type-dot" :style="{ background: getTypeColor(item.type) }"></span>
          <span class="type-name">{{ item.type }}</span>
          <div v-if="item.remark" class="remark-inline">{{ item.remark }}</div>
        </div>
        <div class="col-amount" :class="{ 'amount-income': item.income }">
          {{ formatAmount(item) }}
        </div>
        <div class="col-date">
          <div class="date-primary">{{ formatExpenseDate(item.date) }}</div>
        </div>
        <div class="col-deleted">
          <div class="deleted-primary">{{ formatExpenseDate(item.deletedAt) }}</div>
          <span class="expires-badge" :class="expiresClass(item)">
            <FontAwesomeIcon icon="hourglass-half" />
            {{ expiresText(item) }}
          </span>
        </div>
        <div class="col-actions">
          <button class="row-btn restore" @click="handleRestoreOne(item)">
            <FontAwesomeIcon icon="undo" />
            {{ t('recycleBin.restore') }}
          </button>
          <button class="row-btn delete" @click="handlePermanentDeleteOne(item)">
            <FontAwesomeIcon icon="trash" />
            {{ t('recycleBin.permanentDelete') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Mobile cards -->
    <div class="mobile-list">
      <div
        v-for="item in sortedExpenses"
        :key="item.id"
        class="deleted-card"
        :class="{ selected: selectedIds.has(item.id) }"
      >
        <div class="card-top">
          <div v-if="selectMode" class="col-check">
            <input
              type="checkbox"
              class="row-checkbox"
              :checked="selectedIds.has(item.id)"
              @change="toggleSelect(item.id)"
            />
          </div>
          <div class="card-main-info">
            <div class="card-type-row">
              <span class="type-dot" :style="{ background: getTypeColor(item.type) }"></span>
              <span class="type-name">{{ item.type }}</span>
            </div>
            <div class="card-date-row">
              <span class="date-label">{{ t('expense.date') }}:</span>
              <span>{{ formatExpenseDate(item.date) }}</span>
            </div>
          </div>
          <div class="col-amount" :class="{ 'amount-income': item.income }">
            {{ formatAmount(item) }}
          </div>
        </div>

        <div v-if="item.remark" class="card-remark">{{ item.remark }}</div>

        <div class="card-deleted-row">
          <div class="deleted-info">
            <FontAwesomeIcon icon="trash-alt" class="del-icon" />
            <span class="del-label">{{ t('recycleBin.deletedAt') }}:</span>
            <span class="del-date">{{ formatExpenseDate(item.deletedAt) }}</span>
          </div>
          <span class="expires-badge" :class="expiresClass(item)">
            <FontAwesomeIcon icon="hourglass-half" />
            {{ expiresText(item) }}
          </span>
        </div>

        <div v-if="!selectMode" class="card-actions">
          <button class="card-btn restore" @click="handleRestoreOne(item)">
            <FontAwesomeIcon icon="undo" />
            {{ t('recycleBin.restore') }}
          </button>
          <button class="card-btn delete" @click="handlePermanentDeleteOne(item)">
            <FontAwesomeIcon icon="trash" />
            {{ t('recycleBin.permanentDelete') }}
          </button>
        </div>
      </div>
    </div>
    </template>

    <!-- Confirmation dialog -->
    <GlassDialog v-model:visible="confirmDialog.visible" :title="confirmDialog.title" width="92%" maxWidth="480px" :z-index="9998">
      <div class="confirm-body">
        <div class="confirm-icon" :class="confirmDialog.variant">
          <FontAwesomeIcon :icon="confirmDialog.icon" />
        </div>
        <p class="confirm-message">{{ confirmDialog.message }}</p>
      </div>
      <template #footer>
        <GlassButton type="secondary" @click="confirmDialog.visible = false">
          {{ t('common.cancel') }}
        </GlassButton>
        <GlassButton
          :type="confirmDialog.variant === 'danger' ? 'warning' : 'primary'"
          :disabled="confirmDialog.executing"
          @click="runConfirmAction"
        >
          <span v-if="confirmDialog.executing" class="inline-spinner" />
          {{ confirmDialog.okLabel }}
        </GlassButton>
      </template>
    </GlassDialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

import GlassButton from '@/components/GlassButton.vue';
import GlassDialog from '@/components/GlassDialog.vue';
import MessageTip from '@/components/MessageTip.vue';
import Header from '@/components/Header.vue';

import { ExpenseAPI } from '@/api/expenses';
import { getTypeColor } from '@/utils/expenseUtils';
import { formatDateByLocale } from '@/utils/dateFormatter';

const router = useRouter();
const { t, locale } = useI18n();

// --- State ---
const loading = ref(true);
const expenses = ref([]);
const successMessage = ref('');
const errorMessage = ref('');

// Selection
const selectMode = ref(false);
const selectedIds = ref(new Set());

// Confirmation dialog generic state
const confirmDialog = reactive({
  visible: false,
  title: '',
  message: '',
  okLabel: '',
  icon: 'exclamation-triangle',
  variant: 'default', // 'default' | 'danger'
  executing: false,
  _action: null,
});

// --- Computed ---
const totalCount = computed(() => expenses.value.length);
const multiSelectLabel = computed(() => t('recycleBin.selectAll')); // select mode entry label
const sortedExpenses = computed(() => {
  return [...expenses.value].sort((a, b) => {
    // Newest deleted first
    const da = new Date(a.deletedAt || 0).getTime();
    const db = new Date(b.deletedAt || 0).getTime();
    return db - da;
  });
});
const isAllSelected = computed(() => totalCount.value > 0 && selectedIds.value.size === totalCount.value);
const isPartiallySelected = computed(() => selectedIds.value.size > 0 && selectedIds.value.size < totalCount.value);
const selectCaption = computed(() =>
  isAllSelected.value ? t('recycleBin.deselectAll') : t('recycleBin.selectAll'),
);

// --- Helpers ---
function formatAmount (item) {
  // Use locale currency if available. Fallback to plain number with sign.
  const currency = t('common.currency') || '';
  const symbol = t('common.currencySymbol') || '';
  const amt = Number(item.amount || 0);
  const abs = Math.abs(amt).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const sign = item.income ? '+' : (amt >= 0 ? '-' : '+'); // expenses default negative prefix if not income
  // Simpler: just use the income flag to choose sign prefix and color
  const prefix = item.income ? '+' : '-';
  return `${symbol}${prefix}${abs}`;
}

function formatExpenseDate (dateStr) {
  if (!dateStr) return '-';
  try {
    return formatDateByLocale(dateStr, locale.value);
  } catch (e) {
    return String(dateStr).slice(0, 10);
  }
}

// Returns { daysLeft, hoursLeft, expired }
function expiresInfo (item) {
  const deletedAt = new Date(item.deletedAt || 0).getTime();
  if (!deletedAt || isNaN(deletedAt)) {
    return { daysLeft: 30, hoursLeft: 0, expired: false, today: false };
  }
  // 30 days retention
  const expiresAt = deletedAt + 30 * 24 * 60 * 60 * 1000;
  const now = Date.now();
  const diffMs = expiresAt - now;
  if (diffMs <= 0) return { daysLeft: 0, hoursLeft: 0, expired: true, today: true };
  const totalHours = diffMs / (60 * 60 * 1000);
  const daysLeft = Math.floor(totalHours / 24);
  const hoursLeft = Math.max(1, Math.round(totalHours));
  return { daysLeft, hoursLeft, expired: false, today: daysLeft === 0 };
}

function expiresText (item) {
  const info = expiresInfo(item);
  if (info.expired || info.today) return t('recycleBin.expiredToday');
  if (info.daysLeft >= 1) return t('recycleBin.expiresIn', { days: info.daysLeft });
  return t('recycleBin.expiresHours', { hours: info.hoursLeft });
}

function expiresClass (item) {
  const info = expiresInfo(item);
  if (info.expired || info.daysLeft <= 1) return 'danger';
  if (info.daysLeft <= 7) return 'warning';
  return 'info';
}

function flashMessage (success, text) {
  errorMessage.value = '';
  successMessage.value = '';
  if (success) successMessage.value = text;
  else errorMessage.value = text;
  // Auto clear
  setTimeout(() => {
    if (success && successMessage.value === text) successMessage.value = '';
    if (!success && errorMessage.value === text) errorMessage.value = '';
  }, 4500);
}

// --- Selection ---
function toggleSelect (id) {
  const next = new Set(selectedIds.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selectedIds.value = next;
}
function toggleSelectAll () {
  if (isAllSelected.value) {
    selectedIds.value = new Set();
  } else {
    selectedIds.value = new Set(expenses.value.map(e => e.id));
  }
}
function cancelSelection () {
  selectMode.value = false;
  selectedIds.value = new Set();
}

// --- Data loading ---
async function loadDeleted () {
  loading.value = true;
  try {
    const { expenses: list } = await ExpenseAPI.getDeletedExpenses();
    expenses.value = list || [];
  } catch (err) {
    console.error('loadDeleted failed:', err);
    flashMessage(false, t('common.loadFailed'));
    expenses.value = [];
  } finally {
    loading.value = false;
  }
}

// --- Generic confirm runner ---
function confirmAction ({ title, message, okLabel, icon = 'exclamation-triangle', variant = 'default', action }) {
  confirmDialog.visible = true;
  confirmDialog.title = title;
  confirmDialog.message = message;
  confirmDialog.okLabel = okLabel;
  confirmDialog.icon = icon;
  confirmDialog.variant = variant;
  confirmDialog._action = action;
  confirmDialog.executing = false;
}
async function runConfirmAction () {
  if (typeof confirmDialog._action !== 'function') {
    confirmDialog.visible = false;
    return;
  }
  confirmDialog.executing = true;
  try {
    await confirmDialog._action();
    confirmDialog.visible = false;
  } catch (err) {
    console.error('confirm action failed:', err);
    flashMessage(false, t('recycleBin.operationFailed'));
  } finally {
    confirmDialog.executing = false;
    confirmDialog._action = null;
  }
}

// --- Single operations ---
function handleRestoreOne (item) {
  confirmAction({
    title: t('recycleBin.restoreSelectedTitle'),
    message: t('recycleBin.restoreSelectedMessage', { count: 1 }),
    okLabel: t('recycleBin.restore'),
    icon: 'undo',
    variant: 'default',
    action: async () => {
      await ExpenseAPI.restoreExpense(item.id);
      expenses.value = expenses.value.filter(e => e.id !== item.id);
      flashMessage(true, t('recycleBin.restoreSuccess'));
    },
  });
}

function handlePermanentDeleteOne (item) {
  confirmAction({
    title: t('recycleBin.permanentDeleteTitle'),
    message: t('recycleBin.permanentDeleteMessage'),
    okLabel: t('recycleBin.permanentDelete'),
    icon: 'skull',
    variant: 'danger',
    action: async () => {
      await ExpenseAPI.hardDeleteExpense(item.id);
      totalCount.value -= 1;
      expenses.value = expenses.value.filter(e => e.id !== item.id);
      flashMessage(true, t('recycleBin.deleteSuccess'));
    },
  });
}

// --- Bulk operations ---
function handleRestoreAll () {
  const count = totalCount.value;
  if (count === 0) return;
  confirmAction({
    title: t('recycleBin.restoreAllTitle'),
    message: t('recycleBin.restoreAllMessage', { count }),
    okLabel: t('recycleBin.restoreAll'),
    icon: 'undo',
    variant: 'default',
    action: async () => {
      await ExpenseAPI.restoreAllExpenses();
      expenses.value = [];
      cancelSelection();
      flashMessage(true, t('recycleBin.restoreAllSuccess'));
    },
  });
}

function handleEmptyBin () {
  const count = totalCount.value;
  if (count === 0) return;
  confirmAction({
    title: t('recycleBin.deleteAllTitle'),
    message: t('recycleBin.deleteAllMessage', { count }),
    okLabel: t('recycleBin.emptyBin'),
    icon: 'exclamation-triangle',
    variant: 'danger',
    action: async () => {
      await ExpenseAPI.permanentDeleteAll();
      expenses.value = [];
      cancelSelection();
      flashMessage(true, t('recycleBin.deleteAllSuccess'));
    },
  });
}

function handleRestoreSelected () {
  const ids = [...selectedIds.value];
  if (ids.length === 0) return;
  confirmAction({
    title: t('recycleBin.restoreSelectedTitle'),
    message: t('recycleBin.restoreSelectedMessage', { count: ids.length }),
    okLabel: t('recycleBin.restoreSelected'),
    icon: 'undo',
    variant: 'default',
    action: async () => {
      await ExpenseAPI.restoreExpensesBatch(ids);
      expenses.value = expenses.value.filter(e => !selectedIds.value.has(e.id));
      cancelSelection();
      flashMessage(true, t('recycleBin.restoreAllSuccess'));
    },
  });
}

function handleDeleteSelected () {
  const ids = [...selectedIds.value];
  if (ids.length === 0) return;
  confirmAction({
    title: t('recycleBin.deleteSelectedTitle'),
    message: t('recycleBin.deleteSelectedMessage', { count: ids.length }),
    okLabel: t('recycleBin.deleteSelected'),
    icon: 'skull',
    variant: 'danger',
    action: async () => {
      await ExpenseAPI.permanentDeleteBatch(ids);
      expenses.value = expenses.value.filter(e => !selectedIds.value.has(e.id));
      cancelSelection();
      flashMessage(true, t('recycleBin.deleteAllSuccess'));
    },
  });
}

// --- Navigation ---
function goBack () {
  // Navigate to home page
  if (router && typeof router.push === 'function') {
    router.push('/').catch(() => {});
  } else if (typeof window !== 'undefined') {
    window.location.hash = '#/';
  }
}

// Header back button: reuse same behavior
function onHeaderBack() {
  goBack();
}

onMounted(() => {
  loadDeleted();
});
</script>

<style scoped src="../styles/views/RecycleBinView.css"></style>
