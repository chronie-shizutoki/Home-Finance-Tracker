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

<style scoped>
.glass-button {
  position: relative;
  padding: 10px 20px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: #1a202c;
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.1);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.glass-button:hover {
  background: rgba(255, 255, 255, 1);
  box-shadow: 0 8px 24px rgba(31, 38, 135, 0.15);
  transform: translateY(-2px);
}

.glass-button:active {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.1);
}

.glass-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* Color variants */
.glass-button.primary {
  background: rgba(59, 130, 246, 0.92);
  color: #ffffff;
  border-color: rgba(59, 130, 246, 0.3);
}

.glass-button.primary:hover {
  background: rgba(59, 130, 246, 1);
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.3);
}

.glass-button.secondary {
  background: rgba(241, 245, 249, 0.95);
  color: #475569;
  border-color: rgba(0, 0, 0, 0.08);
}

.glass-button.secondary:hover {
  background: rgba(241, 245, 249, 1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.glass-button.success {
  background: rgba(34, 197, 94, 0.92);
  color: white;
  border-color: rgba(34, 197, 94, 0.3);
}

.glass-button.success:hover {
  background: rgba(34, 197, 94, 1);
  box-shadow: 0 8px 24px rgba(34, 197, 94, 0.3);
}

.glass-button.warning {
  background: rgba(234, 179, 8, 0.92);
  color: #1a202c;
  border-color: rgba(234, 179, 8, 0.3);
}

.glass-button.warning:hover {
  background: rgba(234, 179, 8, 1);
  box-shadow: 0 8px 24px rgba(234, 179, 8, 0.3);
}

.glass-button.danger {
  background: rgba(239, 68, 68, 0.92);
  color: #ffffff;
  border-color: rgba(239, 68, 68, 0.3);
}

.glass-button.danger:hover {
  background: rgba(239, 68, 68, 1);
  box-shadow: 0 8px 24px rgba(239, 68, 68, 0.3);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-button {
  background: rgba(30, 41, 59, 0.85);
  color: #e2e8f0;
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.glass-button:hover {
  background: rgba(30, 41, 59, 0.95);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.glass-button.primary {
  background: rgba(59, 130, 246, 0.9);
}

.glass-button.primary:hover {
  background: rgba(59, 130, 246, 1);
}

.glass-button.secondary {
  background: rgba(51, 65, 85, 0.85);
  color: #cbd5e1;
}

.glass-button.secondary:hover {
  background: rgba(51, 65, 85, 0.95);
}

.glass-button.success {
  background: rgba(34, 197, 94, 0.9);
}

.glass-button.success:hover {
  background: rgba(34, 197, 94, 1);
}

.glass-button.warning {
  background: rgba(234, 179, 8, 0.9);
  color: #1a202c;
}

.glass-button.warning:hover {
  background: rgba(234, 179, 8, 1);
}

.glass-button.danger {
  background: rgba(239, 68, 68, 0.9);
}

.glass-button.danger:hover {
  background: rgba(239, 68, 68, 1);
}
}
</style>