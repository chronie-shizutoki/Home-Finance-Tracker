<template>
  <div :class="['glass-form-item']">
    <label v-if="label" :class="['glass-form-item-label', { 'required': required }]">
      {{ label }}
    </label>
    <div class="glass-form-item-content">
      <slot></slot>
    </div>
    <div v-if="error" class="glass-form-item-error">
      {{ error }}
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  required: {
    type: Boolean,
    default: false
  },
  prop: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
})
</script>

<style scoped src="../styles/components/GlassFormItem.css"></style>