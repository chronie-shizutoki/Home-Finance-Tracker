<template>
  <form
    :class="['glass-form']"
    @submit.prevent="handleSubmit"
  >
    <slot></slot>
  </form>
</template>

<script setup>
const props = defineProps({
  model: {
    type: Object,
    default: () => ({})
  },
})

const emit = defineEmits(['submit'])

const handleSubmit = () => {
  emit('submit')
}
</script>

<style scoped>
.glass-form {
  width: 100%;
  padding: 24px;
  border-radius: var(--border-radius-lg, 20px);
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.12);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-form {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
}
}
</style>