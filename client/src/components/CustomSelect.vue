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

<style scoped>
.custom-select {
  position: relative;
  width: auto;
  max-width: 60%;
  font-size: 14px;
}

.select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 15px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: all 0.2s ease;
}

.select-trigger:hover {
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.6);
  transition: all 0.2s ease;
}

.select-trigger:focus {
  outline: none;
  border-color: rgba(67, 97, 238, 0.5);
  box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.15);
}

.select-trigger {
  transition: all 0.2s ease;
}

.select-icon {
  width: 16px;
  height: 16px;
  position: relative;
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.select-icon::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  width: 6px;
  height: 6px;
  border-right: 2px solid #495057;
  border-bottom: 2px solid #495057;
  transform: translateY(-60%) rotate(45deg);
}

.custom-select.open .select-icon {
  transform: rotate(180deg);
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.select-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  max-height: 240px;
  overflow-y: auto;
  z-index: 9999;
  transform-origin: top center;
}

/* Vue transition class - Enhanced */
.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.dropdown-fade-enter-from {
  opacity: 0;
  transform: translateY(-15px) scale(0.9);
  visibility: hidden;
}

.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-5px) scale(0.95);
  visibility: hidden;
}

/* Ensure dark mode can see the animation */
:deep(.dropdown-fade-enter-active),
:deep(.dropdown-fade-leave-active) {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

:deep(.dropdown-fade-enter-from),
:deep(.dropdown-fade-leave-to) {
  opacity: 0;
  visibility: hidden;
}

:deep(.dropdown-fade-enter-from) {
  transform: translateY(-15px) scale(0.9);
}

:deep(.dropdown-fade-leave-to) {
  transform: translateY(-5px) scale(0.95);
}

/* Custom scrollbar style - Normal mode */
.select-dropdown::-webkit-scrollbar {
  width: 6px;
}

.select-dropdown::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 3px;
  margin: 4px 0;
}

.select-dropdown::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
  transition: background 0.2s ease;
}

.select-dropdown::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.select-option {
  padding: 10px 15px 10px 30px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.select-option:hover {
  background: #f0f7ff;
  color: #4361ee;
}

.select-option.selected {
  background: transparent;
  color: #4361ee;
  position: relative;
}

.select-option.selected::before {
  content: '✓';
  position: absolute;
  left: 15px;
  font-weight: bold;
}

/* Responsive design - Mobile first */
@media (max-width: 768px) {
  .custom-select {
    max-width: 100%;
  }
  
  .select-trigger {
    padding: 10px 12px;
    font-size: 13px;
  }
}

/* Dark mode adaptation */
@media (prefers-color-scheme: dark) {
  .select-trigger {
    background: rgba(30, 30, 30, 0.5);
    border-color: rgba(255, 255, 255, 0.1);
    color: #e5e7eb;
  }

  .select-trigger:hover {
    background: rgba(30, 30, 30, 0.65);
    border-color: rgba(255, 255, 255, 0.2);
  }

  .select-icon::before {
    border-right-color: #9ca3af;
    border-bottom-color: #9ca3af;
  }

  .select-dropdown {
    background: rgba(30, 30, 30, 0.85);
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  }

  /* Custom scrollbar style - Dark mode */
  .select-dropdown::-webkit-scrollbar {
    width: 6px;
  }

  .select-dropdown::-webkit-scrollbar-track {
    background: #374151;
    border-radius: 3px;
    margin: 4px 0;
  }

  .select-dropdown::-webkit-scrollbar-thumb {
    background: #4b5563;
    border-radius: 3px;
    transition: background 0.2s ease;
  }

  .select-dropdown::-webkit-scrollbar-thumb:hover {
    background: #6b7280;
  }
  
  .select-option {
    color: #e5e7eb;
  }
  
  .select-option:hover {
    background: #374151;
    color: #a5b4fc;
  }
  
  .select-option.selected {
    background: transparent;
    color: #a5b4fc;
    position: relative;
  }

  .select-option.selected::before {
    content: '✓';
    position: absolute;
    left: 15px;
    font-weight: bold;
  }
}
</style>