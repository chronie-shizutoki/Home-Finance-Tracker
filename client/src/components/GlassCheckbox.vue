<template>
  <div :class="['glass-checkbox-container']">
    <label :class="['glass-checkbox-wrapper', { 'disabled': disabled }]">
      <input 
        ref="checkboxRef"
        type="checkbox"
        :checked="modelValue"
        @change="handleChange"
        :disabled="disabled"
        class="glass-checkbox-input"
      />
      <div
        :class="['glass-checkbox-box', { 'checked': modelValue, 'disabled': disabled }]"
        v-liquid-glass
      >
        <slot name="check-icon">
          <svg v-if="modelValue" width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11.6667 3.83334L5.66671 9.83334L2.33337 6.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </slot>
      </div>
      <span v-if="label" class="glass-checkbox-label">{{ label }}</span>
    </label>
    <div v-if="error" class="glass-checkbox-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const checkboxRef = ref(null)

// Watch for external value changes
watch(
  () => props.modelValue,
  (newVal) => {
    if (checkboxRef.value) {
      checkboxRef.value.checked = newVal
    }
  }
)

// Handle change event
const handleChange = (e) => {
  const checked = e.target.checked
  emit('update:modelValue', checked)
  emit('change', checked)
}
</script>

<style scoped src="../styles/components/GlassCheckbox.css"></style>