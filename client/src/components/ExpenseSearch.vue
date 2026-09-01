<!-- ExpenseSearch.vue -->
<template>
    <div class="search-container" v-liquid-glass>
      <div class="search-header">
        <h2>{{ $t('expense.search.title') }}</h2>
        <div class="search-actions">
          <GlassButton @click="handleReset" class="reset-button">
            <i class="icon-reset"></i>
            {{ $t('expense.search.reset') }}
          </GlassButton>
        </div>
      </div>

      <div class="search-grid">
        <!-- Month selection -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-calendar"></i>
            {{ $t('expense.search.month') }}
          </label>
          <div class="control-input">
            <!-- Month selection component -->
            <CustomSelect
              v-model="month"
              :options="monthOptions"
              :empty-option-label="$t('expense.search.allMonth')"
              :value-formatter="(value) => value ? formatMonthLabelByLocale(value, props.locale) : ''"
            />
          </div>
        </div>

        <!-- Type selection -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-category"></i>
            {{ $t('expense.search.type') }}
          </label>
          <div class="control-input">
            <!-- Type selection component -->
            <CustomSelect
              v-model="type"
              :options="uniqueTypes"
              :empty-option-label="$t('expense.search.allType')"
            />
          </div>
        </div>

        <!-- Sort option -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-sort"></i>
            {{ $t('expense.search.sort') }}
          </label>
          <div class="control-input">
            <!-- Sort option component -->
            <CustomSelect
              v-model="sortOption"
              :options="sortOptions"
              :include-empty-option="false"
              :value-formatter="(value) => {
                if (!sortOptions || sortOptions.length === 0 || !value) return '';
                const option = sortOptions.find(opt => opt.value === value);
                return option ? option.label : '';
              }"
            />
          </div>
        </div>

        <!-- Amount range selection -->
        <div class="search-control">
          <label class="control-label" for="minAmount">
            <i class="icon-amount"></i>
            {{ $t('expense.search.amountRange') }}
          </label>
          <div class="amount-range">
            <div class="range-input">
              <input type="number" v-model.number="minAmount" id="minAmount" name="minAmount" min="0" step="0.01"
                     :placeholder="$t('expense.search.minAmountPlaceholder')">
              <span class="range-divider">-</span>
              <input type="number" v-model.number="maxAmount" id="maxAmount" name="maxAmount" min="0" step="0.01"
                     :placeholder="$t('expense.search.maxAmountPlaceholder')">
            </div>
            <div class="range-slider">
              <input type="range" min="0" :max="maxSliderValue" step="10"
                     v-model.number="minAmount" class="slider min-slider">
              <input type="range" min="0" :max="maxSliderValue" step="10"
                     v-model.number="maxAmount" class="slider max-slider">
            </div>
          </div>
        </div>

        <!-- Keyword search input -->
        <div class="search-control">
          <label class="control-label" for="keyword">
            <i class="icon-keyword"></i>
            {{ $t('expense.search.keyword') }}
          </label>
          <div class="control-input">
            <input type="text" v-model.trim="keyword" id="keyword" name="keyword"
                   :placeholder="$t('expense.search.keywordPlaceholder')">
          </div>
        </div>
      </div>

      <div v-if="activeFiltersCount > 0" class="active-filters">
        <div class="filter-badge" v-if="month">
          {{ $t('expense.search.month') }}: {{ monthDisplay }}
          <span @click="clearFilter('month')" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="type">
          {{ $t('expense.search.type') }}: {{ type }}
          <span @click="clearFilter('type')" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="minAmount || maxAmount">
          {{ $t('expense.search.amountRange') }}:
          {{ minAmount ? minAmount : '0' }} - {{ maxAmount ? maxAmount : '∞' }}
          <span @click="clearAmountFilter" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="keyword">
          {{ $t('expense.search.keyword') }}: "{{ keyword }}"
          <span @click="clearFilter('keyword')" class="clear-filter">×</span>
        </div>
      </div>
    </div>
  </template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { formatMonthLabelByLocale } from '@/utils/dateFormatter';
import CustomSelect from './CustomSelect.vue';

const props = defineProps({
  uniqueTypes: Array,
  initialKeyword: String,
  initialType: String,
  initialMonth: String,
  initialMinAmount: [String, Number],
  initialMaxAmount: [String, Number],
  initialSortOption: String,
  locale: { type: String, default: 'en-US' },
  maxAmountRange: { type: Number, default: 99999999 }, // Default maximum amount range
  availableMonths: { type: Array, default: () => [] }
});

const emit = defineEmits(['search']);

// Reactive search conditions
const keyword = ref(props.initialKeyword || '');
const type = ref(props.initialType || '');
const month = ref(props.initialMonth || '');
const minAmount = ref(props.initialMinAmount || '');
const maxAmount = ref(props.initialMaxAmount || '');
const sortOption = ref(props.initialSortOption || 'dateDesc');
const monthOptions = ref([]);
const { t, locale } = useI18n();

// Sort option data
const sortOptions = computed(() => [
  { value: 'dateDesc', label: t('expense.sort.dateDesc') },
  { value: 'dateAsc', label: t('expense.sort.dateAsc') },
  { value: 'amountDesc', label: t('expense.sort.amountDesc') },
  { value: 'amountAsc', label: t('expense.sort.amountAsc') }
]);

// Computed properties
const monthDisplay = computed(() => {
  if (!month.value) return '';
  return formatMonthLabelByLocale(month.value, props.locale);
});

const activeFiltersCount = computed(() => {
  let count = 0;
  if (month.value) count++;
  if (type.value) count++;
  if (minAmount.value || maxAmount.value) count++;
  if (keyword.value) count++;
  return count;
});

const maxSliderValue = computed(() => {
  return props.maxAmountRange || 5000;
});

// Computed search parameters object
const searchParams = computed(() => {
  // Validate and convert numeric types
  const min = minAmount.value !== '' ? Number(minAmount.value) : undefined;
  const max = maxAmount.value !== '' ? Number(maxAmount.value) : undefined;

  // Ensure numeric validity
  const validMin = !isNaN(min) ? min : undefined;
  const validMax = !isNaN(max) ? max : undefined;

  return {
    keyword: keyword.value,
    type: type.value,
    month: month.value,
    minAmount: validMin,
    maxAmount: validMax,
    sort: sortOption.value
  };
});

// Generate month options based on table data, default to last 12 months
const generateMonthOptions = () => {
  let options = [];

  // if available months data is valid
  if (props.availableMonths?.length) {
    // Map available months to objects with value and label
    options = props.availableMonths.map(month => {
      // Normalize locale to support more formats
      const localeMap = {
        en: 'en-US'
      };
      const normalizedLocale = localeMap[props.locale] || props.locale || 'en-US';
      return {
        value: month,
        label: formatMonthLabelByLocale(month, normalizedLocale)
      };
    });
  }

  monthOptions.value = options;
};

// Search processing
const handleSearch = () => {
  console.log('Search initiated with params:', searchParams.value);
  
  // Validate amount range logic
  const { minAmount, maxAmount } = searchParams.value;
  if (minAmount !== undefined && maxAmount !== undefined && minAmount > maxAmount) {
    console.warn('Amount range invalid - min > max, swapping values', { minAmount, maxAmount });
    // Swap min and max if min is greater than max
    minAmount.value = maxAmount;
    maxAmount.value = minAmount;
    return;
  }

  // Use validated search params
  const searchData = {
    ...searchParams.value,
    sortOption: sortOption.value // Maintain backward compatibility with existing code
  };
  
  console.log('Emitting search event:', searchData);
  emit('search', searchData);
};

// Reset search filters
const handleReset = () => {
  console.log('Resetting all search filters');
  keyword.value = '';
  type.value = '';
  month.value = '';
  minAmount.value = '';
  maxAmount.value = '';
  sortOption.value = 'dateDesc';
  console.log('Filters reset completed, triggering search');
  handleSearch();
};

// Clear single filter
const clearFilter = (filterName) => {
  console.log('Clearing filter:', filterName);
  switch (filterName) {
  case 'month':
    month.value = '';
    break;
  case 'type':
    type.value = '';
    break;
  case 'keyword':
    keyword.value = '';
    break;
  }
  handleSearch();
};

// Clear amount range filter
const clearAmountFilter = () => {
  console.log('Clearing amount range filters');
  minAmount.value = '';
  maxAmount.value = '';
  handleSearch();
};

// Initialize month options
onMounted(() => {
  console.log('ExpenseSearch component mounted with initial props:', {
    locale: props.locale,
    uniqueTypesCount: props.uniqueTypes?.length || 0,
    availableMonthsCount: props.availableMonths?.length || 0
  });
  generateMonthOptions();
});

// Listen for changes in availableMonths to update month options
watch(() => props.availableMonths, () => {
  generateMonthOptions();
}, { deep: true });

// Listen for changes in locale to update month options
watch(locale, (newLocale) => {
  console.log('ExpenseSearch: re-generated month options for locale changed to:', newLocale);
  generateMonthOptions();
});

// Listen for changes in props.locale to update month options
watch(() => props.locale, (newLocale) => {
  console.log('ExpenseSearch: re-generated month options for locale changed to:', newLocale);
  generateMonthOptions();
});

// Listen for changes in all filter conditions
watch([keyword, type, month, minAmount, maxAmount, sortOption], (newValues) => {
  console.log('Filter condition changed:', {
    keyword: newValues[0],
    type: newValues[1],
    month: newValues[2],
    minAmount: newValues[3],
    maxAmount: newValues[4],
    sortOption: newValues[5]
  });
  handleSearch();
}, { deep: true });

// Expose methods to parent component
defineExpose({
  handleReset
});
</script>

  <style scoped src="../styles/components/ExpenseSearch.css"></style>
