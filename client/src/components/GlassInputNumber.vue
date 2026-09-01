<template>
  <div :class="['glass-input-number-container']">
    <div v-if="label" class="glass-input-number-label">{{ label }}</div>
    <div :class="['glass-input-number-wrapper', { 'disabled': disabled }]">
      <button 
        class="glass-input-number-btn decrease"
        @click="decrease"
        :disabled="disabled || modelValue <= min"
      >
        <slot name="decrease-icon">−</slot>
      </button>
      <div class="glass-input-number-content">
        <div v-if="prefix" class="glass-input-number-prefix">{{ prefix }}</div>
        <input 
          ref="inputRef"
          type="number"
          :value="modelValue"
          @input="handleInput"
          @focus="handleFocus"
          @blur="handleBlur"
          @keydown.up="handleKeyUp"
          @keydown.down="handleKeyDown"
          :min="min"
          :max="max"
          :step="step"
          :disabled="disabled"
          :placeholder="placeholder"
          :class="['glass-input-number']"
        />
        <div v-if="suffix" class="glass-input-number-suffix">{{ suffix }}</div>
      </div>
      <button 
        class="glass-input-number-btn increase"
        @click="increase"
        :disabled="disabled || modelValue >= max"
      >
        <slot name="increase-icon">+</slot>
      </button>
    </div>
    <div v-if="error" class="glass-input-number-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  },
  label: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  min: {
    type: Number,
    default: -Infinity
  },
  max: {
    type: Number,
    default: Infinity
  },
  step: {
    type: Number,
    default: 1
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  prefix: {
    type: String,
    default: ''
  },
  suffix: {
    type: String,
    default: ''
  },
  precision: {
    type: Number,
    default: 0
  },
  size: {
    type: String,
    default: 'medium'
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const internalValue = ref(props.modelValue)

// Keep the value within range
const clampValue = (value) => {
  return Math.max(props.min, Math.min(props.max, value))
}

// Watch for external value changes
watch(
  () => props.modelValue,
  (newVal) => {
    internalValue.value = clampValue(newVal)
  }
)

// Increment value
const increase = () => {
  if (props.disabled || internalValue.value >= props.max) return
  const newValue = clampValue(internalValue.value + props.step)
  updateValue(newValue)
}

// Decrement value
const decrease = () => {
  if (props.disabled || internalValue.value <= props.min) return
  const newValue = clampValue(internalValue.value - props.step)
  updateValue(newValue)
}

// Handle input event
const handleInput = (e) => {
  let value = parseFloat(e.target.value)

  if (isNaN(value)) {
    // Keep empty when input is blank or non-numeric
    internalValue.value = ''
    return
  }

  value = clampValue(value)
  internalValue.value = value
  emit('update:modelValue', value)
  emit('change', value)
}

// Handle focus
const handleFocus = () => {
  emit('focus')
}

// Handle blur
const handleBlur = () => {
  if (internalValue.value === '') {
    // Reset to default value when left blank on blur
    const defaultValue = clampValue(props.min)
    updateValue(defaultValue)
  }
  emit('blur')
}

// Handle ArrowUp key
const handleKeyUp = (e) => {
  e.preventDefault()
  increase()
}

// Handle ArrowDown key
const handleKeyDown = (e) => {
  e.preventDefault()
  decrease()
}

// Shared value-update helper
const updateValue = (newValue) => {
  internalValue.value = newValue
  emit('update:modelValue', newValue)
  emit('change', newValue)
}
</script>

<style scoped src="../styles/components/GlassInputNumber.css"></style>