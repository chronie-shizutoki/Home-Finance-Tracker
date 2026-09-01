<template>
  <button
    :class="[
      'glass-button',
      {
        'primary': type === 'primary',
        'secondary': type === 'secondary',
        'success': type === 'success',
        'warning': type === 'warning',
        'danger': type === 'danger'
      },
      $attrs.class
    ]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot name="icon"></slot>
    <slot></slot>
  </button>
</template>

<script setup>

const props = defineProps({
  type: {
    type: String,
    default: 'default', // default, primary, secondary, success, warning, danger
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['click'])
</script>

<style scoped src="../styles/components/GlassButton.css"></style>