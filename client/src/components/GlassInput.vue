<template>
  <div :class="['glass-input-container']">
    <label v-if="label" :class="['glass-input-label', { 'error': error }]">{{ label }}</label>
    <div :class="['glass-input-wrapper', { 'focused': isFocused, 'error': error }]">
      <slot name="prefix"></slot>
      <!-- Input type -->
      <div v-if="type !== 'textarea'" style="flex: 1; position: relative;">
        <input
          :type="type"
          :placeholder="placeholder"
          :value="modelValue"
          :disabled="disabled"
          :maxlength="maxlength > 0 ? maxlength : undefined"
          @input="$emit('update:modelValue', $event.target.value)"
          @focus="isFocused = true"
          @blur="isFocused = false"
          class="glass-input"
        >
        <div v-if="showWordLimit && maxlength > 0" class="glass-input-count">
          {{ modelValue.length }}/{{ maxlength }}
        </div>
      </div>
      <!-- Textarea type -->
      <div v-else style="flex: 1; position: relative; width: 100%;">
        <textarea
          :placeholder="placeholder"
          :value="modelValue"
          :disabled="disabled"
          :rows="rows"
          :maxlength="maxlength > 0 ? maxlength : undefined"
          @input="$emit('update:modelValue', $event.target.value)"
          @focus="isFocused = true"
          @blur="isFocused = false"
          class="glass-textarea"
          style="width: 100%;"
        ></textarea>
        <div v-if="showWordLimit && maxlength > 0" class="glass-input-count">
          {{ modelValue.length }}/{{ maxlength }}
        </div>
      </div>
      <slot name="suffix"></slot>
    </div>
    <div v-if="errorMessage" class="glass-input-error">{{ errorMessage }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  type: {
    type: String,
    default: 'text',
  },
  placeholder: {
    type: String,
    default: '',
  },
  label: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  error: {
    type: Boolean,
    default: false,
  },
  errorMessage: {
    type: String,
    default: '',
  },
  rows: {
    type: Number,
    default: 3,
  },
  maxlength: {
    type: Number,
    default: 0,
  },
  showWordLimit: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['update:modelValue'])

const isFocused = ref(false)
</script>

<style scoped>
.glass-input-container {
  margin-bottom: 16px;
  width: 100%;
}

.glass-input-label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #1a202c;
  transition: all 0.2s ease;
}

.glass-input-label.error {
  color: #e53e3e;
}

.glass-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  padding: 0 12px;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.08);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.glass-input-wrapper:hover {
  background: rgba(255, 255, 255, 1);
  border-color: rgba(0, 0, 0, 0.12);
}

.glass-input-wrapper.focused {
  background: rgba(255, 255, 255, 1);
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.glass-input-wrapper.error {
  border-color: rgba(229, 62, 62, 0.5);
  box-shadow: 0 0 0 3px rgba(229, 62, 62, 0.12);
}

.glass-input {
  padding: 10px 0;
  border: none;
  outline: none;
  font-size: 14px;
  color: #1a202c;
  background: transparent;
}

.glass-textarea {
  padding: 12px 0;
  border: none;
  outline: none;
  font-size: 14px;
  color: #1a202c;
  background: transparent;
  resize: vertical;
}

.glass-input::placeholder,
.glass-textarea::placeholder {
  color: #a0aec0;
}

.glass-input:disabled,
.glass-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.glass-input-count {
  position: absolute;
  right: 12px;
  bottom: 6px;
  font-size: 11px;
  color: #a0aec0;
}

.glass-input-error {
  margin-top: 4px;
  font-size: 12px;
  color: #e53e3e;
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-input-container .glass-input-label {
  color: #e2e8f0;
}

.glass-input-container .glass-input-wrapper {
  background: rgba(30, 41, 59, 0.85);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.glass-input-container .glass-input-wrapper:hover {
  background: rgba(30, 41, 59, 0.95);
  border-color: rgba(255, 255, 255, 0.18);
}

.glass-input-container .glass-input-wrapper.focused {
  background: rgba(30, 41, 59, 0.95);
  border-color: rgba(96, 165, 250, 0.6);
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.15);
}

.glass-input-container .glass-input,
.glass-input-container .glass-textarea {
  color: #e2e8f0;
}

.glass-input-container .glass-input::placeholder,
.glass-input-container .glass-textarea::placeholder {
  color: #94a3b8;
}

.glass-input-container .glass-input-count {
  color: #94a3b8;
}

.glass-input-container .glass-input-error {
  color: #fc8181;
}
}
</style>