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

<style scoped>
/* Unified container content: keep in sync with ChartsView / MembershipView layout. */
:deep(.container) {
  max-width: 1200px;
  margin: 0 auto;
  box-sizing: border-box;
}

/* Stats bar: total count + auto-purge hint (below the unified Header) */
.stats-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 80px 0 18px 0;
  padding: 0 4px;
}
.record-count-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #666;
  padding: 4px 12px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 999px;
}

/* ----- Toolbar ----- */
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 14px 16px;
  margin-bottom: 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}
/* Default mode (not multi-select): push all action buttons to the right.
   The .toolbar-spacer (added below) is the primary mechanism. */
.toolbar:not(.select-mode-bar) {
  justify-content: flex-start;
}
.toolbar-spacer {
  flex: 1 1 auto;
  min-width: 12px;
  height: 1px;
}
/* In selection mode: left block stays left, action buttons are pushed right
   via the existing .flex-grow spacer. */
.toolbar.select-mode-bar {
  justify-content: flex-start;
  border-color: rgba(67, 97, 238, 0.35);
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.08), rgba(114, 9, 183, 0.06));
}
/* Mobile: keep action buttons left-aligned so they fit within the viewport
   edge next to the list. */
@media (max-width: 768px) {
  .toolbar-spacer { display: none; }
}
.select-block {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.select-all-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  font-size: 14px;
  color: #333;
}
.select-all-checkbox,
.row-checkbox {
  width: 18px;
  height: 18px;
  accent-color: #4361ee;
  cursor: pointer;
}
.selected-count {
  font-size: 13px;
  color: #4361ee;
  font-weight: 600;
  background: rgba(67, 97, 238, 0.12);
  padding: 4px 10px;
  border-radius: 999px;
}
.flex-grow {
  flex: 1;
}

/* ----- Loading / Empty ----- */
.loading-wrap {
  padding: 60px 20px;
  text-align: center;
  color: #777;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.loading-spinner {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 3px solid rgba(67, 97, 238, 0.2);
  border-top-color: #4361ee;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.empty-state {
  padding: 80px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: #555;
}
.empty-icon {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #aaa;
  font-size: 40px;
  margin-bottom: 8px;
}
.empty-state h2 {
  margin: 0;
  font-size: 20px;
  color: #333;
}
.empty-hint {
  max-width: 460px;
  margin: 0 auto;
  font-size: 13px;
  color: #777;
  line-height: 1.6;
}

/* ----- Desktop list ----- */
.desktop-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.row {
  display: grid;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  grid-template-columns: 44px 1.6fr 1fr 1fr 1.3fr 1.4fr;
  grid-template-areas: "check type amount date deleted actions";
  transition: background 0.15s ease;
}
.row:not(.list-header):nth-child(even) {
  background: rgba(0, 0, 0, 0.02);
}
.row:not(.list-header):hover {
  background: rgba(67, 97, 238, 0.05);
}
.row.selected {
  background: rgba(67, 97, 238, 0.1) !important;
}
.list-header {
  font-weight: 600;
  color: #555;
  font-size: 13px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(0, 0, 0, 0.02);
}
.col-check { grid-area: check; }
.col-type { grid-area: type; display: flex; flex-direction: column; gap: 4px; }
.col-amount { grid-area: amount; font-weight: 700; font-size: 15px; color: #e63946; }
.col-amount.amount-income { color: #2a9d8f; }
.col-date { grid-area: date; }
.col-deleted { grid-area: deleted; display: flex; flex-direction: column; gap: 6px; }
.col-actions { grid-area: actions; display: flex; gap: 8px; justify-content: flex-end; flex-wrap: wrap; }

.type-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.type-name {
  font-weight: 600;
  color: #222;
}
.col-type .type-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.remark-inline {
  color: #777;
  font-size: 12px;
  padding-left: 18px;
  word-break: break-word;
}
.date-primary,
.deleted-primary {
  font-size: 14px;
  color: #333;
}

.expires-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  width: fit-content;
}
.expires-badge.info {
  background: rgba(42, 157, 143, 0.12);
  color: #2a9d8f;
}
.expires-badge.warning {
  background: rgba(244, 162, 97, 0.18);
  color: #c2722a;
}
.expires-badge.danger {
  background: rgba(230, 57, 70, 0.15);
  color: #c1121f;
}

.row-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.2s ease;
}
.row-btn.restore {
  background: rgba(67, 97, 238, 0.14);
  color: #4361ee;
}
.row-btn.restore:hover {
  background: #4361ee;
  color: white;
  transform: translateY(-1px);
}
.row-btn.delete {
  background: rgba(230, 57, 70, 0.14);
  color: #e63946;
}
.row-btn.delete:hover {
  background: #e63946;
  color: white;
  transform: translateY(-1px);
}

/* ----- Confirm dialog ----- */
.confirm-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 6px 4px 10px;
  text-align: center;
}
.confirm-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}
.confirm-icon.default {
  background: rgba(67, 97, 238, 0.12);
  color: #4361ee;
}
.confirm-icon.danger {
  background: rgba(230, 57, 70, 0.15);
  color: #e63946;
}
.confirm-message {
  margin: 0;
  color: #333;
  font-size: 15px;
  line-height: 1.6;
}
.inline-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: currentColor;
  animation: spin 0.8s linear infinite;
  margin-right: 6px;
  vertical-align: middle;
}

/* ----- Mobile list ----- */
.mobile-list {
  display: none;
  flex-direction: column;
  gap: 14px;
}
.deleted-card {
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  padding: 14px 14px 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  transition: background 0.15s ease, border-color 0.15s ease;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.deleted-card.selected {
  border-color: rgba(67, 97, 238, 0.5);
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.08), rgba(255, 255, 255, 0.5));
}
.card-top {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.card-main-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-type-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #222;
  font-size: 15px;
}
.card-date-row {
  font-size: 12px;
  color: #777;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.date-label {
  opacity: 0.8;
}
.card-top .col-amount {
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}
.card-remark {
  font-size: 13px;
  color: #555;
  padding: 8px 10px;
  background: rgba(0, 0, 0, 0.035);
  border-radius: 10px;
  line-height: 1.5;
  word-break: break-word;
}
.card-deleted-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.025);
}
.deleted-info {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #555;
  flex-wrap: wrap;
}
.del-icon {
  color: #e63946;
}
.del-label {
  font-weight: 600;
  color: #777;
}
.card-actions {
  display: flex;
  gap: 10px;
  padding-top: 2px;
}
.card-btn {
  flex: 1;
  padding: 10px 8px;
  border-radius: 12px;
  border: none;
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s ease;
}
.card-btn.restore {
  background: rgba(67, 97, 238, 0.14);
  color: #4361ee;
}
.card-btn.restore:active {
  background: #4361ee;
  color: white;
}
.card-btn.delete {
  background: rgba(230, 57, 70, 0.14);
  color: #e63946;
}
.card-btn.delete:active {
  background: #e63946;
  color: white;
}

/* ----- Breakpoints ----- */
@media (min-width: 769px) {
  .desktop-list { display: flex; }
  .mobile-list { display: none; }
}
@media (max-width: 768px) {
  .desktop-list { display: none; }
  .mobile-list { display: flex; }
  .toolbar { padding: 10px; gap: 8px; }
  .toolbar .select-block { width: 100%; justify-content: space-between; }
  .flex-grow { display: none; }
  .toolbar.select-mode-bar button { flex: 1; min-width: 0; }
  .stats-bar { margin: 60px 0 14px 0; }
  .record-count-hint { font-size: 12px; }
}
/* ----- Dark mode ----- */
@media (prefers-color-scheme: dark) {
  .record-count-hint {
    background: rgba(255, 255, 255, 0.08);
    color: #B0B4BD;
  }
  .stats-bar .stat-icon {
    color: #a5b4fc;
  }
  .stats-bar .stat-value {
    color: #E4E6EB;
  }
  .stats-bar .stat-label {
    color: #9EA3AD;
  }
  .toolbar,
  .desktop-list,
  .deleted-card {
    background: rgba(48, 51, 58, 0.85);
    border-color: rgba(255, 255, 255, 0.10);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
  }
  .list-header {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.12);
    color: #B0B4BD;
  }
  .row:not(.list-header):nth-child(even) {
    background: rgba(255, 255, 255, 0.03);
  }
  .row:not(.list-header):hover {
    background: rgba(99, 102, 241, 0.10);
  }
  .row.selected {
    background: rgba(99, 102, 241, 0.16) !important;
  }
  .type-name,
  .date-primary,
  .deleted-primary,
  .select-caption,
  .card-type-row { color: #E4E6EB; }
  .remark-inline,
  .date-label,
  .empty-hint { color: #9EA3AD; }
  .empty-state { color: #B0B4BD; }
  .empty-state h2 { color: #E4E6EB; }
  .empty-icon {
    background: rgba(255, 255, 255, 0.08);
    color: #8a8f98;
  }
  .card-remark {
    background: rgba(255, 255, 255, 0.05);
    color: #C9CCD3;
  }
  .card-deleted-row {
    background: rgba(255, 255, 255, 0.05);
  }
  .deleted-info { color: #B0B4BD; }
  .del-label { color: #8a8f98; }
  .confirm-message { color: #E4E6EB; }

  /* Dark mode card buttons */
  .card-btn.restore {
    background: rgba(99, 102, 241, 0.18);
    color: #a5b4fc;
  }
  .card-btn.restore:hover {
    background: rgba(99, 102, 241, 0.28);
  }
  .card-btn.delete {
    background: rgba(239, 83, 80, 0.18);
    color: #f87171;
  }
  .card-btn.delete:hover {
    background: rgba(239, 83, 80, 0.28);
  }
}

/* ---- Fade transition for message tips ---- */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
