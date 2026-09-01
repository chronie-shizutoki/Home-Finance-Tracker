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

<style scoped src="../styles/components/GlassInput.css"></style>