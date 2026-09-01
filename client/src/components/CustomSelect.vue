<template>
  <div class="custom-select" :class="{ 'open': isOpen }" @click.stop="toggleDropdown">
    <div class="select-trigger" ref="triggerRef">
      <span>{{ displayText }}</span>
      <i class="select-icon"></i>
    </div>
    <transition name="dropdown-fade">
      <div v-if="isOpen" class="select-dropdown" ref="dropdownRef">
        <div 
          v-if="includeEmptyOption" 
          class="select-option" 
          :class="{ 'selected': !modelValue && modelValue !== 0 }" 
          @click.stop="selectValue('')"
        >
          {{ emptyOptionLabel }}
        </div>
        <div 
          v-for="option in options" 
          :key="typeof option === 'object' ? option[props.optionValueKey] : option" 
          class="select-option" 
          :class="{ 'selected': isSelected(option) }" 
          @click.stop="selectValue(typeof option === 'object' ? option[props.optionValueKey] : option)"
        >
          {{ typeof option === 'object' ? option[props.optionLabelKey] : option }}
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  options: {
    type: Array,
    required: true
  },
  emptyOptionLabel: {
    type: String,
    default: 'All'
  },
  includeEmptyOption: {
    type: Boolean,
    default: true
  },
  valueFormatter: {
    type: Function,
    default: (value) => value
  },
  optionValueKey: {
    type: String,
    default: 'value'
  },
  optionLabelKey: {
    type: String,
    default: 'label'
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

const isOpen = ref(false);
const triggerRef = ref(null);
const dropdownRef = ref(null);

// Calculate display text
const displayText = computed(() => {
  try {
    // Handle empty value case
    if (!props.modelValue && props.modelValue !== 0) {
      return props.emptyOptionLabel;
    }
    
    // Force string comparison to handle type differences
    const modelValueStr = String(props.modelValue);
    
    // 1. First check for value match in options
    // Support both object and non-object format
    for (const option of props.options) {
      if (typeof option === 'object') {
        // Object format option
        const optionValue = option[props.optionValueKey];
        
        // Try multiple comparison methods to handle type differences
        const isMatch = 
          optionValue === props.modelValue || 
          String(optionValue) === modelValueStr ||
          (typeof optionValue === 'string' && typeof props.modelValue === 'string' && 
           optionValue.trim() === props.modelValue.trim());
        
        if (isMatch) {
          // Found match, return its label property
          const label = option[props.optionLabelKey];
          return label !== undefined && label !== null ? String(label) : modelValueStr;
        }
      } else {
        // Non-object format option, compare directly
        if (option === props.modelValue || String(option) === modelValueStr) {
          return String(option);
        }
      }
    }
    
    // 2. If direct match fails, try to find in option labels
    // This is to handle special cases like option order change
    for (const option of props.options) {
      if (typeof option === 'object') {
        const optionValue = option[props.optionValueKey];
        // Try loose comparison to handle type differences
        if (String(optionValue).toLowerCase() === modelValueStr.toLowerCase()) {
          return String(option[props.optionLabelKey]);
        }
      }
    }
    
    // 3. If no match found, use custom formatter
    if (props.valueFormatter) {
      try {
        return props.valueFormatter(props.modelValue);
      } catch (err) {
        console.error('Error in custom value formatter:', err.message);
      }
    }
    
    // 4. If all else fails, fallback to modelValue string representation
    // Record the case where no match is found, to avoid excessive logging
    if (Math.random() < 0.2) { // 20% probability
      console.warn('No exact match found for value:', { 
        value: props.modelValue,
        optionsCount: props.options?.length || 0 
      });
    }
    
    return modelValueStr;
  } catch (error) {
    console.error('Error in displayText computation:', { 
      error: error.message,
      modelValue: props.modelValue
    });
    return String(props.modelValue || props.emptyOptionLabel);
  }
});

// Toggle dropdown visibility
const toggleDropdown = () => {
  const newState = !isOpen.value;
  console.log('Dropdown toggle:', { newState, componentId: props.modelValue?.toString() || 'unknown' });
  isOpen.value = newState;
};

// Select value
const selectValue = (value) => {
  console.log('Select option changed:', {
    componentId: props.modelValue?.toString() || 'unknown',
    from: props.modelValue,
    to: value
  });
  emit('update:modelValue', value);
  emit('change', value);
  isOpen.value = false;
};

// Check if selected option
const isSelected = (option) => {
  if (!props.modelValue && props.modelValue !== 0) {
    return false;
  }
  
  const modelValueStr = String(props.modelValue);
  
  if (typeof option === 'object') {
    const optionValue = option[props.optionValueKey];
    // Try multiple comparison methods to handle type differences
    const isMatch = optionValue === props.modelValue || 
           String(optionValue) === modelValueStr ||
           (typeof optionValue === 'string' && typeof props.modelValue === 'string' &&
            optionValue.trim() === props.modelValue.trim());
    
    // Record the case where object type option is matched, to avoid excessive logging
    if (isMatch && Math.random() < 0.1) { // 10% probability
      console.log('Object option matched:', {
        option: option[props.optionLabelKey],
        value: optionValue
      });
    }
    
    return isMatch;
  } else {
    const isMatch = option === props.modelValue || String(option) === modelValueStr;
    
    // Record the case where non-object type option is matched, to avoid excessive logging
    if (isMatch && Math.random() < 0.1) { // 10% probability
      console.log('Simple option matched:', { option });
    }
    
    return isMatch;
  }
};

// Click outside to close dropdown
const handleClickOutside = (event) => {
  if (triggerRef.value && !triggerRef.value.contains(event.target) &&
      dropdownRef.value && !dropdownRef.value.contains(event.target)) {
    console.log('Click outside detected, closing dropdown');
    isOpen.value = false;
  }
};

// Listen for keyboard events
const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    isOpen.value = false;
  }
};

onMounted(() => {
  console.log('CustomSelect component mounted:', {
    optionsCount: props.options?.length || 0,
    includeEmptyOption: props.includeEmptyOption,
    initialValue: props.modelValue
  });
  
  document.addEventListener('click', handleClickOutside);
  document.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
  document.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped src="../styles/components/CustomSelect.css"></style>